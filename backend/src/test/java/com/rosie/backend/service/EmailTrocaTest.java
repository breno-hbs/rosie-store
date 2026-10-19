package com.rosie.backend.service;

import com.rosie.backend.model.MotivoTroca;
import com.rosie.backend.model.Pedido;
import com.rosie.backend.model.Produto;
import com.rosie.backend.model.StatusTroca;
import com.rosie.backend.model.Tamanho;
import com.rosie.backend.model.Troca;
import com.rosie.backend.model.Usuario;
import com.rosie.backend.service.EnviadorEmail.Email;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// Conteúdo do e-mail da troca (#65), montado em memória: sem banco, fila nem envio
class EmailTrocaTest {

    private static final String LOJA = "https://rosiestore.com.br";

    private final EmailTroca emailTroca = new EmailTroca(null, LOJA + "/", "", "");

    @Test
    void aprovadaPorDefeitoDizQueOFreteEDaRosieEOProximoPasso() {
        Email email = emailTroca.montar(troca(StatusTroca.APROVADA, MotivoTroca.DEFEITO), "Pode mandar pelos Correios");

        assertThat(email.para()).isEqualTo("maria@rosie.test");
        assertThat(email.assunto()).isEqualTo("Troca do pedido #270 aprovada");
        assertThat(email.chave()).isEqualTo("troca-33-APROVADA");
        assertThat(email.texto())
                .contains("Olá, Maria!", "Sua troca foi aprovada")
                .contains("Pedido: #270", "Peça: Conjunto Serpente", "Tamanho: M", "Quantidade: 2", "Situação: Aprovada")
                .contains("o frete de devolução é por conta da Rosie")
                .contains("Próximo passo:")
                .contains("Recado da Rosie:\nPode mandar pelos Correios")
                .contains("Ver minhas trocas: https://rosiestore.com.br/minha-conta/trocas");
        assertThat(email.html())
                .contains("ROSIE", "Sua troca foi aprovada", "Conjunto Serpente", "Pode mandar pelos Correios")
                .contains("href=\"https://rosiestore.com.br/minha-conta/trocas\"")
                // Marca: paleta em hex, sem imagem, sombra nem gradiente
                .contains(ModeloEmail.PAPEL, ModeloEmail.TINTA)
                .doesNotContain("<img", "shadow", "gradient", "var(--");
    }

    @Test
    void aprovadaPorqueNaoServiuDizQueOFreteEDaCliente() {
        Email email = emailTroca.montar(troca(StatusTroca.APROVADA, MotivoTroca.NAO_SERVIU), null);

        assertThat(email.texto())
                .contains("Como a peça não serviu, o frete de devolução é por sua conta.")
                .doesNotContain("Recado da Rosie");
    }

    @Test
    void recusadaTrazOMotivoEComoFalarComALoja() {
        EmailTroca comWhatsapp = new EmailTroca(null, LOJA, "+55 (11) 99999-0000", "rosie@rosie.test");

        Email email = comWhatsapp.montar(troca(StatusTroca.RECUSADA, MotivoTroca.NAO_SERVIU), "A peça foi usada");

        assertThat(email.assunto()).isEqualTo("Troca do pedido #270 não aprovada");
        assertThat(email.texto())
                .contains("Sua troca não foi aprovada", "Situação: Recusada")
                .contains("Motivo:\nA peça foi usada")
                .contains("Responda este e-mail ou fale com a Rosie pelo WhatsApp ou pelo Instagram.")
                .contains("Falar no WhatsApp: https://wa.me/5511999990000")
                .contains("Instagram @rosie.sttore: " + EmailTroca.INSTAGRAM)
                .contains("pedir a troca de novo");
    }

    @Test
    void recusadaSemRecadoMandaFalarComALoja() {
        Email email = emailTroca.montar(troca(StatusTroca.RECUSADA, MotivoTroca.DEFEITO), null);

        assertThat(email.texto())
                .contains("Para saber o motivo, fale com a Rosie pelos contatos abaixo.")
                .contains("Dúvidas? Fale com a Rosie pelo Instagram.")
                .doesNotContain("Motivo:", "WhatsApp", "Responda este e-mail");
    }

    @Test
    void concluidaSemRecado() {
        Email email = emailTroca.montar(troca(StatusTroca.CONCLUIDA, MotivoTroca.DEFEITO), null);

        assertThat(email.assunto()).isEqualTo("Troca do pedido #270 concluída");
        assertThat(email.chave()).isEqualTo("troca-33-CONCLUIDA");
        assertThat(email.texto())
                .contains("Sua troca foi concluída", "Situação: Concluída", "A peça voltou para a Rosie")
                .doesNotContain("Recado da Rosie", "frete de devolução");
    }

    @Test
    void recadoENomeComHtmlSaoEscapados() {
        Troca troca = troca(StatusTroca.APROVADA, MotivoTroca.DEFEITO);
        troca.getPedido().getUsuario().setNome("<b>Maria</b> Cliente");

        Email email = emailTroca.montar(troca,
                "<script>alert('oi')</script> & <a href=\"https://golpe.test\">clique</a>\r\nSegunda linha");

        assertThat(email.html())
                .doesNotContain("<script>", "<b>Maria", "href=\"https://golpe.test\"")
                .contains("&lt;script&gt;alert(&#39;oi&#39;)&lt;/script&gt; &amp; &lt;a href=&quot;https://golpe.test&quot;&gt;")
                .contains("&lt;b&gt;Maria&lt;/b&gt;")
                .contains("clique&lt;/a&gt;<br>Segunda linha");
        // O texto puro leva o recado como foi escrito
        assertThat(email.texto()).contains("<script>alert('oi')</script> & <a href=\"https://golpe.test\">clique</a>\nSegunda linha");
    }

    private static Troca troca(StatusTroca status, MotivoTroca motivo) {
        Usuario cliente = Usuario.builder().id(4244L).nome("Maria Cliente").email("maria@rosie.test").build();
        Pedido pedido = Pedido.builder().id(270L).usuario(cliente).build();
        Produto produto = Produto.builder().id(1L).nome("Conjunto Serpente").build();
        return Troca.builder().id(33L).pedido(pedido).produto(produto).tamanho(Tamanho.M).quantidade(2)
                .motivo(motivo).status(status).build();
    }
}
