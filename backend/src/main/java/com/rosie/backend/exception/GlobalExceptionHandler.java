package com.rosie.backend.exception;

import com.rosie.backend.dto.ErroResponseDTO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

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
}
