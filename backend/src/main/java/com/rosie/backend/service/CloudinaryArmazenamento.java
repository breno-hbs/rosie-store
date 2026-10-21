package com.rosie.backend.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.rosie.backend.exception.ArmazenamentoIndisponivelException;

import java.util.Map;

// Fotos no Cloudinary (decisão na #15), com o SDK oficial. Credenciais só via CLOUDINARY_URL.
public class CloudinaryArmazenamento implements ArmazenamentoImagens {

    private final Cloudinary cloudinary;

    public CloudinaryArmazenamento(Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }

    @Override
    public ImagemArmazenada enviar(byte[] conteudo, String pasta) {
        try {
            Map<?, ?> resposta = cloudinary.uploader().upload(conteudo, ObjectUtils.asMap(
                    "folder", pasta,
                    "resource_type", "image"));
            return new ImagemArmazenada((String) resposta.get("secure_url"), (String) resposta.get("public_id"));
        } catch (Exception e) {
            throw new ArmazenamentoIndisponivelException("Não foi possível enviar a foto agora. Tente de novo.", e);
        }
    }

    @Override
    public void remover(String publicId) {
        try {
            // invalidate tira a foto do cache da CDN também
            cloudinary.uploader().destroy(publicId, ObjectUtils.asMap("invalidate", true));
        } catch (Exception e) {
            throw new ArmazenamentoIndisponivelException("Não foi possível remover a foto do Cloudinary", e);
        }
    }
}
