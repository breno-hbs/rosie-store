<#
.SYNOPSIS
    Sobe a demonstração da Rosie (#77): a API com o perfil demo, o site e, com -Tunel, o link temporário.

.DESCRIPTION
    Tudo roda no seu computador, sem nenhuma conta paga:
      - API (Spring Boot) com o perfil demo: frete, etiqueta e pagamento simulados, e-mail só no log, banco
        rosie_demo separado do rosie_db de desenvolvimento (backend/src/main/resources/application-demo.properties).
      - Site compilado (npm run demo) em http://localhost:5173, chamando a API pelo proxy /api do Vite.
      - Com -Tunel: um Cloudflare Quick Tunnel (cloudflared, sem conta) para o site, com um link
        https://<aleatorio>.trycloudflare.com para abrir no celular. O link morre quando o script termina.

    Ctrl+C encerra tudo (API, site e túnel). Passo a passo e roteiro: docs/demonstracao.md.

    Java: usa -JavaHome, senão o JAVA_HOME (se apontar para uma JDK 17 ou mais nova), senão procura uma JDK em
    ~\.jdks (as baixadas pelo IntelliJ), C:\Program Files\Eclipse Adoptium, \Java e \Microsoft. O JAVA_HOME
    só vale enquanto o script roda: nada muda nas variáveis do Windows.

.PARAMETER Tunel
    Abre também o link público temporário (precisa do cloudflared: winget install --id Cloudflare.cloudflared).

.PARAMETER JavaHome
    Pasta da JDK a usar (ex.: "$HOME\.jdks\openjdk-26.0.2.1"), se a encontrada sozinha não servir.

.EXAMPLE
    .\scripts\demo.ps1 -Tunel
#>
[CmdletBinding()]
param(
    [switch]$Tunel,
    [string]$JavaHome
)

$ErrorActionPreference = 'Stop'

$raiz = Split-Path -Parent $PSScriptRoot
$backend = Join-Path $raiz 'backend'
$frontend = Join-Path $raiz 'frontend'
$pastaLogs = Join-Path $env:TEMP 'rosie-demo'
$logApi = Join-Path $pastaLogs 'api.log'
$logApiErros = Join-Path $pastaLogs 'api-erros.log'
$logSite = Join-Path $pastaLogs 'site.log'
$logSiteErros = Join-Path $pastaLogs 'site-erros.log'
$logTunel = Join-Path $pastaLogs 'tunel.log'
$logTunelSaida = Join-Path $pastaLogs 'tunel-saida.log'

$portaApi = 8080
$portaSite = 5173
$urlLocal = "http://localhost:$portaSite"
$emailAdmin = 'admin@rosie.demo'

function Passo([string]$texto) { Write-Host "==> $texto" -ForegroundColor Cyan }
function Aviso([string]$texto) { Write-Host "    $texto" -ForegroundColor Yellow }
function Falhar([string]$texto) {
    Write-Host ''
    Write-Host "ERRO: $texto" -ForegroundColor Red
    exit 1
}

function PortaOcupada([int]$porta) {
    $cliente = New-Object System.Net.Sockets.TcpClient
    try {
        return $cliente.ConnectAsync('127.0.0.1', $porta).Wait(700)
    } catch {
        return $false
    } finally {
        $cliente.Dispose()
    }
}

function UrlResponde([string]$url) {
    try {
        $resposta = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 5
        return $resposta.StatusCode -eq 200
    } catch {
        return $false
    }
}

# Versão principal de uma JDK pelo arquivo "release" (JAVA_VERSION="17.0.12" → 17), ou 0 se não for uma JDK
function VersaoJdk([string]$pasta) {
    if (-not $pasta -or -not (Test-Path (Join-Path $pasta 'bin\java.exe'))) { return 0 }
    $release = Join-Path $pasta 'release'
    if (-not (Test-Path $release)) { return 0 }
    $linha = Select-String -Path $release -Pattern '^JAVA_VERSION="(\d+)' | Select-Object -First 1
    if (-not $linha) { return 0 }
    return [int]$linha.Matches[0].Groups[1].Value
}

function EncontrarJdk {
    if ($JavaHome) {
        if ((VersaoJdk $JavaHome) -ge 17) { return $JavaHome }
        Falhar "-JavaHome '$JavaHome' não é uma JDK 17 ou mais nova (falta bin\java.exe ou o arquivo release)."
    }
    if ((VersaoJdk $env:JAVA_HOME) -ge 17) { return $env:JAVA_HOME }
    $lugares = @(
        (Join-Path $HOME '.jdks'),
        (Join-Path $env:ProgramFiles 'Eclipse Adoptium'),
        (Join-Path $env:ProgramFiles 'Java'),
        (Join-Path $env:ProgramFiles 'Microsoft')
    )
    $jdks = foreach ($lugar in $lugares) {
        if (Test-Path $lugar) {
            Get-ChildItem $lugar -Directory | ForEach-Object {
                $versao = VersaoJdk $_.FullName
                if ($versao -ge 17) { [pscustomobject]@{ Pasta = $_.FullName; Versao = $versao } }
            }
        }
    }
    # A de versão mais baixa a partir da 17: a mais perto da do CI e do Railway
    $escolhida = $jdks | Sort-Object Versao | Select-Object -First 1
    if ($escolhida) { return $escolhida.Pasta }
    return $null
}

function EncontrarCloudflared {
    $comando = Get-Command cloudflared -ErrorAction SilentlyContinue
    if ($comando) { return $comando.Source }
    # O winget instala aqui; num terminal aberto antes da instalação o PATH ainda não tem a pasta
    foreach ($pasta in @(${env:ProgramFiles(x86)}, $env:ProgramFiles)) {
        if ($pasta) {
            $exe = Join-Path $pasta 'cloudflared\cloudflared.exe'
            if (Test-Path $exe) { return $exe }
        }
    }
    return $null
}

# Senha do admin da demo: só vale na primeira subida do banco rosie_demo (depois o admin já existe)
function GerarSenha {
    $alfabeto = 'abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789'
    $bytes = New-Object byte[] 14
    $gerador = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $gerador.GetBytes($bytes)
    $gerador.Dispose()
    return -join ($bytes | ForEach-Object { $alfabeto[$_ % $alfabeto.Length] })
}

function UltimasLinhas([string]$arquivo) {
    if (Test-Path $arquivo) { Get-Content $arquivo -Tail 25 | ForEach-Object { Write-Host "    $_" } }
}

# mvnw e npm abrem processos filhos (java, node): mata a árvore inteira
function PararArvore($processo) {
    if ($processo -and -not $processo.HasExited) {
        & taskkill.exe /PID $processo.Id /T /F 2>&1 | Out-Null
    }
}

# ---------------------------------------------------------------------------------------------------------------
Write-Host ''
Write-Host 'Rosie: demonstração (#77)' -ForegroundColor Magenta
Write-Host ''

Passo 'Conferindo o computador'

$local = Join-Path $backend 'src\main\resources\application-local.properties'
if (-not (Test-Path $local) -and (-not $env:DB_PASSWORD -or -not $env:JWT_SECRET)) {
    Falhar ("Falta o backend\src\main\resources\application-local.properties (senha do MySQL, JWT e Cloudinary). " +
            "Copie o do repositório principal ou o application-example.properties e preencha (README, Configuração).")
}

if (-not (PortaOcupada 3306)) {
    Falhar 'O MySQL não está respondendo na porta 3306. Ligue o serviço (services.msc → MySQL80 → Iniciar) e rode de novo.'
}
foreach ($porta in @($portaApi, $portaSite)) {
    if (PortaOcupada $porta) {
        Falhar ("A porta $porta já está em uso (a API ou o npm run dev de desenvolvimento ainda abertos?). " +
                "Feche-os e rode de novo.")
    }
}

$jdk = EncontrarJdk
if (-not $jdk) {
    Falhar ('Nenhuma JDK 17 ou mais nova encontrada. Instale (winget install EclipseAdoptium.Temurin.17.JDK) ' +
            'ou passe a pasta: .\scripts\demo.ps1 -JavaHome "C:\caminho\da\jdk".')
}
Write-Host "    Java: $jdk (só para este script)"

$npm = Get-Command npm.cmd -ErrorAction SilentlyContinue
if (-not $npm) { Falhar 'O Node.js não está instalado (winget install OpenJS.NodeJS.LTS).' }

$cloudflared = $null
if ($Tunel) {
    $cloudflared = EncontrarCloudflared
    if (-not $cloudflared) {
        Write-Host ''
        Write-Host 'O cloudflared (do link temporário) não está instalado. Instale com:' -ForegroundColor Yellow
        Write-Host ''
        Write-Host '    winget install --id Cloudflare.cloudflared' -ForegroundColor White
        Write-Host ''
        Write-Host 'Depois feche e abra o terminal e rode de novo. Sem o link, rode sem -Tunel.' -ForegroundColor Yellow
        exit 1
    }
    Write-Host "    Túnel: $cloudflared"
}

if (-not (Test-Path (Join-Path $frontend 'node_modules'))) {
    Passo 'Instalando as dependências do site (npm ci, só na primeira vez)'
    Push-Location $frontend
    try {
        & $npm.Source ci
        if ($LASTEXITCODE -ne 0) { Falhar 'npm ci falhou.' }
    } finally {
        Pop-Location
    }
}

New-Item -ItemType Directory -Force $pastaLogs | Out-Null
Remove-Item $logApi, $logApiErros, $logSite, $logSiteErros, $logTunel, $logTunelSaida -ErrorAction SilentlyContinue

# Variáveis só deste processo e dos filhos; voltam como estavam no fim
$nomesVariaveis = @('JAVA_HOME', 'SPRING_PROFILES_ACTIVE', 'ROSIE_LOJA_URL', 'ROSIE_DEMO_ADMIN_SENHA')
$variaveisAntes = @{}
foreach ($nome in $nomesVariaveis) { $variaveisAntes[$nome] = [Environment]::GetEnvironmentVariable($nome, 'Process') }

$api = $null
$site = $null
$tunelProcesso = $null
$urlPublica = $null

try {
    # 1. Túnel primeiro: a API precisa saber o link (vai no link da etiqueta simulada da troca)
    if ($Tunel) {
        Passo 'Abrindo o link temporário (Cloudflare Quick Tunnel)'
        $tunelProcesso = Start-Process -FilePath $cloudflared `
            -ArgumentList @('tunnel', '--no-autoupdate', '--url', "http://127.0.0.1:$portaSite") `
            -RedirectStandardError $logTunel -RedirectStandardOutput $logTunelSaida -WindowStyle Hidden -PassThru
        $limite = (Get-Date).AddSeconds(60)
        while (-not $urlPublica -and (Get-Date) -lt $limite) {
            Start-Sleep -Seconds 1
            if ($tunelProcesso.HasExited) { break }
            if (Test-Path $logTunel) {
                $achado = Select-String -Path $logTunel -Pattern 'https://[a-z0-9-]+\.trycloudflare\.com' |
                    Select-Object -First 1
                if ($achado) { $urlPublica = $achado.Matches[0].Value }
            }
        }
        if (-not $urlPublica) {
            UltimasLinhas $logTunel
            Falhar 'O cloudflared não devolveu o link (sem internet ou bloqueado pelo firewall?). Rode sem -Tunel para seguir só no seu computador.'
        }
        Write-Host "    Link: $urlPublica"
    }

    # 2. API com o perfil demo
    Passo 'Subindo a API (perfil demo, banco rosie_demo); a primeira vez demora mais'
    $env:JAVA_HOME = $jdk
    $env:SPRING_PROFILES_ACTIVE = 'demo'
    if ($urlPublica) { $env:ROSIE_LOJA_URL = $urlPublica } else { $env:ROSIE_LOJA_URL = $urlLocal }
    $senhaGerada = $null
    if (-not $variaveisAntes['ROSIE_DEMO_ADMIN_SENHA']) {
        $senhaGerada = GerarSenha
        $env:ROSIE_DEMO_ADMIN_SENHA = $senhaGerada
    }
    $api = Start-Process -FilePath (Join-Path $backend 'mvnw.cmd') -ArgumentList @('-q', 'spring-boot:run') `
        -WorkingDirectory $backend -RedirectStandardOutput $logApi -RedirectStandardError $logApiErros `
        -WindowStyle Hidden -PassThru
    $limite = (Get-Date).AddMinutes(5)
    while (-not (UrlResponde "http://localhost:$portaApi/actuator/health")) {
        if ($api.HasExited) {
            UltimasLinhas $logApi
            UltimasLinhas $logApiErros
            Falhar "A API não subiu. Log completo: $logApi"
        }
        if ((Get-Date) -gt $limite) {
            UltimasLinhas $logApi
            Falhar "A API não respondeu em 5 minutos. Log completo: $logApi"
        }
        Start-Sleep -Seconds 2
    }
    $adminCriado = [bool](Select-String -Path $logApi -Pattern 'Admin inicial criado' -Quiet)

    # 3. Site compilado no modo demo, com o proxy /api
    Passo 'Compilando e servindo o site (npm run demo)'
    $site = Start-Process -FilePath $npm.Source -ArgumentList @('run', 'demo') -WorkingDirectory $frontend `
        -RedirectStandardOutput $logSite -RedirectStandardError $logSiteErros -WindowStyle Hidden -PassThru
    $limite = (Get-Date).AddMinutes(2)
    while (-not (UrlResponde "http://127.0.0.1:$portaSite/")) {
        if ($site.HasExited -or (Get-Date) -gt $limite) {
            UltimasLinhas $logSite
            UltimasLinhas $logSiteErros
            Falhar "O site não subiu. Log completo: $logSite"
        }
        Start-Sleep -Seconds 1
    }
    if (-not (UrlResponde "http://127.0.0.1:$portaSite/api/actuator/health")) {
        Falhar "O site subiu, mas o proxy /api não alcança a API. Logs: $logSite e $logApi"
    }

    # 4. O link do túnel leva mesmo ao site e à API? (o endereço novo leva alguns segundos para valer)
    $tunelOk = $false
    if ($urlPublica) {
        Passo 'Conferindo o link temporário'
        $limite = (Get-Date).AddSeconds(60)
        while (-not $tunelOk -and (Get-Date) -lt $limite) {
            $tunelOk = (UrlResponde "$urlPublica/") -and (UrlResponde "$urlPublica/api/actuator/health")
            if (-not $tunelOk) { Start-Sleep -Seconds 3 }
        }
        if (-not $tunelOk) { Aviso 'O link ainda não respondeu daqui; teste no celular em alguns segundos.' }
        try { Set-Clipboard -Value $urlPublica } catch { }
    }

    Write-Host ''
    Write-Host '=========================================================================' -ForegroundColor Green
    Write-Host ' Demonstração no ar' -ForegroundColor Green
    Write-Host '=========================================================================' -ForegroundColor Green
    Write-Host " Loja, no seu computador:  $urlLocal"
    Write-Host " Painel da Roseane:        $urlLocal/admin/login"
    if ($urlPublica) {
        Write-Host " Link para o celular:      $urlPublica" -ForegroundColor White
        if ($tunelOk) { Write-Host '                           (copiado; conferido: site e API respondem)' }
        Write-Host '                           Vale só enquanto este terminal estiver aberto.'
    } else {
        Write-Host ' Sem link para o celular (rode com -Tunel para abrir um).'
    }
    Write-Host ''
    if ($adminCriado) {
        Write-Host " Admin da demo criado agora: $emailAdmin" -ForegroundColor Yellow
        if ($senhaGerada) {
            Write-Host " Senha (anote; não aparece de novo e não fica salva): $senhaGerada" -ForegroundColor Yellow
        } else {
            Write-Host ' Senha: a de ROSIE_DEMO_ADMIN_SENHA.' -ForegroundColor Yellow
        }
    } else {
        Write-Host " Admin da demo: $emailAdmin, com a senha anotada na primeira subida."
    }
    Write-Host ''
    Write-Host ' Pagamento, frete e etiqueta simulados; e-mails só no log. Nada é cobrado nem enviado.'
    Write-Host " Logs: $pastaLogs"
    Write-Host ''
    Write-Host ' Ctrl+C encerra tudo (API, site e túnel). Não feche só a janela.' -ForegroundColor Green
    Write-Host '=========================================================================' -ForegroundColor Green

    while ($true) {
        Start-Sleep -Seconds 2
        if ($api.HasExited) { UltimasLinhas $logApi; Falhar "A API parou. Log: $logApi" }
        if ($site.HasExited) { UltimasLinhas $logSite; Falhar "O site parou. Log: $logSite" }
        if ($tunelProcesso -and $tunelProcesso.HasExited) {
            UltimasLinhas $logTunel
            Falhar "O túnel caiu (o link parou de funcionar). Rode o script de novo: o link novo será outro."
        }
    }
} finally {
    Write-Host ''
    Passo 'Encerrando a demonstração'
    PararArvore $tunelProcesso
    PararArvore $site
    PararArvore $api
    foreach ($nome in $nomesVariaveis) { [Environment]::SetEnvironmentVariable($nome, $variaveisAntes[$nome], 'Process') }
    if ($tunelProcesso) { Write-Host '    Túnel fechado: o link não abre mais.' }
    Write-Host '    API e site parados.'
}
