package com.rosie.backend.exception;

import com.rosie.backend.dto.ErroResponseDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Set<String> REGRAS_DE_OBRIGATORIO = Set.of("NotBlank", "NotNull", "NotEmpty");

    @Value("${rosie.imagens.tamanho-maximo}")
    private DataSize tamanhoMaximoImagem;

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ResponseEntity<ErroResponseDTO> handleNaoEncontrado(RecursoNaoEncontradoException ex) {
        ErroResponseDTO erro = new ErroResponseDTO(ex.getMessage(), "RECURSO_NAO_ENCONTRADO");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(erro);
    }

    @ExceptionHandler(CredenciaisInvalidasException.class)
    public ResponseEntity<ErroResponseDTO> handleCredenciaisInvalidas(CredenciaisInvalidasException ex) {
        ErroResponseDTO erro = new ErroResponseDTO(ex.getMessage(), "CREDENCIAIS_INVALIDAS");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(erro);
    }

    // Senha atual errada na troca de e-mail (#61): 403, não 401, para a loja não encerrar a sessão
    @ExceptionHandler(SenhaIncorretaException.class)
    public ResponseEntity<ErroResponseDTO> handleSenhaIncorreta(SenhaIncorretaException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErroResponseDTO(ex.getMessage(), "SENHA_INCORRETA"));
    }

    // Link da troca de e-mail (#61) ou da redefinição de senha (#93): TOKEN_INVALIDO ou TOKEN_EXPIRADO
    @ExceptionHandler(TokenInvalidoException.class)
    public ResponseEntity<ErroResponseDTO> handleTokenInvalido(TokenInvalidoException ex) {
        return ResponseEntity.badRequest().body(new ErroResponseDTO(ex.getMessage(), ex.getCodigo()));
    }

    // Esqueci minha senha acima do limite por e-mail ou por IP (#93)
    @ExceptionHandler(MuitasTentativasException.class)
    public ResponseEntity<ErroResponseDTO> handleMuitasTentativas(MuitasTentativasException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new ErroResponseDTO(ex.getMessage(), "MUITAS_TENTATIVAS"));
    }

    // Regra de negócio que impede a operação (ex.: CATEGORIA_DUPLICADA, CATEGORIA_EM_USO)
    @ExceptionHandler(ConflitoException.class)
    public ResponseEntity<ErroResponseDTO> handleConflito(ConflitoException ex) {
        ErroResponseDTO erro = new ErroResponseDTO(ex.getMessage(), ex.getCodigo());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(erro);
    }

    // Bean Validation (@Valid): devolve a mensagem do primeiro campo inválido. O Hibernate Validator
    // não garante a ordem das violações (#68), então a escolha é fixa: o campo que vem antes no DTO e,
    // no mesmo campo, a regra de obrigatório ("Informe o telefone") antes da de formato.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErroResponseDTO> handleDadosInvalidos(MethodArgumentNotValidException ex) {
        List<String> campos = camposNaOrdem(ex.getBindingResult().getTarget());
        String mensagem = ex.getBindingResult().getFieldErrors().stream()
                .min(Comparator.comparingInt((FieldError erro) -> posicao(campos, erro.getField()))
                        .thenComparingInt(erro -> REGRAS_DE_OBRIGATORIO.contains(erro.getCode()) ? 0 : 1)
                        .thenComparing(FieldError::getField)
                        .thenComparing(erro -> Objects.toString(erro.getDefaultMessage(), "")))
                .map(FieldError::getDefaultMessage)
                .orElse("Dados inválidos");
        return ResponseEntity.badRequest().body(new ErroResponseDTO(mensagem, "DADOS_INVALIDOS"));
    }

    private static List<String> camposNaOrdem(Object dto) {
        if (dto == null || !dto.getClass().isRecord()) {
            return List.of();
        }
        return Arrays.stream(dto.getClass().getRecordComponents()).map(RecordComponent::getName).toList();
    }

    // Campo aninhado ("itens[0].tamanho") conta pela posição do campo de fora ("itens")
    private static int posicao(List<String> campos, String campo) {
        int indice = campos.indexOf(campo.split("[.\\[]", 2)[0]);
        return indice < 0 ? Integer.MAX_VALUE : indice;
    }

    // Regra de negócio fora do Bean Validation (ex.: categoria inexistente no cadastro de produto)
    @ExceptionHandler(DadosInvalidosException.class)
    public ResponseEntity<ErroResponseDTO> handleRegraInvalida(DadosInvalidosException ex) {
        return ResponseEntity.badRequest().body(new ErroResponseDTO(ex.getMessage(), "DADOS_INVALIDOS"));
    }

    // JSON malformado ou corpo ausente
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErroResponseDTO> handleCorpoInvalido(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest()
                .body(new ErroResponseDTO("Corpo da requisição inválido", "DADOS_INVALIDOS"));
    }

    // Upload de foto: FORMATO_NAO_SUPORTADO ou ARQUIVO_MUITO_GRANDE
    @ExceptionHandler(ArquivoInvalidoException.class)
    public ResponseEntity<ErroResponseDTO> handleArquivoInvalido(ArquivoInvalidoException ex) {
        return ResponseEntity.badRequest().body(new ErroResponseDTO(ex.getMessage(), ex.getCodigo()));
    }

    // Arquivo acima de spring.servlet.multipart.max-file-size: barrado antes de chegar ao controller
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErroResponseDTO> handleArquivoGrande(MaxUploadSizeExceededException ex) {
        ArquivoInvalidoException erro = ArquivoInvalidoException.muitoGrande(tamanhoMaximoImagem);
        return ResponseEntity.badRequest().body(new ErroResponseDTO(erro.getMessage(), erro.getCodigo()));
    }

    // Upload sem o campo "arquivo", ou requisição que não é multipart. Banner (#89) sem a parte "dados".
    @ExceptionHandler({MissingServletRequestPartException.class, MultipartException.class})
    public ResponseEntity<ErroResponseDTO> handleSemArquivo(Exception ex) {
        String mensagem = ex instanceof MissingServletRequestPartException faltando
                && !"arquivo".equals(faltando.getRequestPartName())
                ? "Envie o campo " + faltando.getRequestPartName()
                : "Envie a foto no campo arquivo";
        return ResponseEntity.badRequest().body(new ErroResponseDTO(mensagem, "DADOS_INVALIDOS"));
    }

    // Sem CLOUDINARY_URL, ou o Cloudinary não respondeu
    @ExceptionHandler(ArmazenamentoIndisponivelException.class)
    public ResponseEntity<ErroResponseDTO> handleArmazenamentoIndisponivel(ArmazenamentoIndisponivelException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErroResponseDTO(ex.getMessage(), "ARMAZENAMENTO_INDISPONIVEL"));
    }

    // Sem MELHOR_ENVIO_TOKEN / ROSIE_CEP_ORIGEM, ou token recusado pelo Melhor Envio
    @ExceptionHandler(FreteIndisponivelException.class)
    public ResponseEntity<ErroResponseDTO> handleFreteIndisponivel(FreteIndisponivelException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErroResponseDTO(ex.getMessage(), "FRETE_INDISPONIVEL"));
    }

    // Melhor Envio fora do ar, lento demais ou com resposta inesperada
    @ExceptionHandler(FreteFalhouException.class)
    public ResponseEntity<ErroResponseDTO> handleFreteFalhou(FreteFalhouException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ErroResponseDTO(ex.getMessage(), "FRETE_FALHOU"));
    }

    // Sem token, CEP de origem ou dados do remetente, ou token recusado pelo Melhor Envio (#25)
    @ExceptionHandler(EtiquetaIndisponivelException.class)
    public ResponseEntity<ErroResponseDTO> handleEtiquetaIndisponivel(EtiquetaIndisponivelException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErroResponseDTO(ex.getMessage(), "ETIQUETA_INDISPONIVEL"));
    }

    // Melhor Envio recusou a etiqueta, caiu ou demorou demais (#25)
    @ExceptionHandler(EtiquetaFalhouException.class)
    public ResponseEntity<ErroResponseDTO> handleEtiquetaFalhou(EtiquetaFalhouException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ErroResponseDTO(ex.getMessage(), "ETIQUETA_FALHOU"));
    }

    // Sem as variáveis do Mercado Pago, ou access token recusado
    @ExceptionHandler(PagamentoIndisponivelException.class)
    public ResponseEntity<ErroResponseDTO> handlePagamentoIndisponivel(PagamentoIndisponivelException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErroResponseDTO(ex.getMessage(), "PAGAMENTO_INDISPONIVEL"));
    }

    // Mercado Pago fora do ar, lento demais ou com resposta inesperada
    @ExceptionHandler(PagamentoFalhouException.class)
    public ResponseEntity<ErroResponseDTO> handlePagamentoFalhou(PagamentoFalhouException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ErroResponseDTO(ex.getMessage(), "PAGAMENTO_FALHOU"));
    }

    // Link de confirmação da troca de e-mail que o Resend não aceitou (#61). Os outros e-mails saem pela
    // FilaEmail, que só registra a falha no log, e nunca chegam aqui.
    @ExceptionHandler(EmailFalhouException.class)
    public ResponseEntity<ErroResponseDTO> handleEmailFalhou(EmailFalhouException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new ErroResponseDTO(
                "Não foi possível enviar o e-mail agora. Tente de novo em instantes.", "EMAIL_FALHOU"));
    }

    // Webhook com assinatura que não confere: a mensagem não diz o motivo
    @ExceptionHandler(AssinaturaInvalidaException.class)
    public ResponseEntity<ErroResponseDTO> handleAssinaturaInvalida(AssinaturaInvalidaException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErroResponseDTO("Assinatura inválida", "ASSINATURA_INVALIDA"));
    }
}
