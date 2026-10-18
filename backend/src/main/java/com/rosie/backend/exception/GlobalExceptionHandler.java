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

@RestControllerAdvice
public class GlobalExceptionHandler {

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

    // Regra de negócio que impede a operação (ex.: CATEGORIA_DUPLICADA, CATEGORIA_EM_USO)
    @ExceptionHandler(ConflitoException.class)
    public ResponseEntity<ErroResponseDTO> handleConflito(ConflitoException ex) {
        ErroResponseDTO erro = new ErroResponseDTO(ex.getMessage(), ex.getCodigo());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(erro);
    }

    // Bean Validation (@Valid): devolve a mensagem do primeiro campo inválido
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErroResponseDTO> handleDadosInvalidos(MethodArgumentNotValidException ex) {
        String mensagem = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("Dados inválidos");
        return ResponseEntity.badRequest().body(new ErroResponseDTO(mensagem, "DADOS_INVALIDOS"));
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

    // Upload sem o campo "arquivo", ou requisição que não é multipart
    @ExceptionHandler({MissingServletRequestPartException.class, MultipartException.class})
    public ResponseEntity<ErroResponseDTO> handleSemArquivo(Exception ex) {
        return ResponseEntity.badRequest()
                .body(new ErroResponseDTO("Envie a foto no campo arquivo", "DADOS_INVALIDOS"));
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

    // Webhook com assinatura que não confere: a mensagem não diz o motivo
    @ExceptionHandler(AssinaturaInvalidaException.class)
    public ResponseEntity<ErroResponseDTO> handleAssinaturaInvalida(AssinaturaInvalidaException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErroResponseDTO("Assinatura inválida", "ASSINATURA_INVALIDA"));
    }
}
