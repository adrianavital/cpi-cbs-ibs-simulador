# Integração SOAP – Calculadora (serviço público de estudo)

iFlow `Calculadora_SOAP`, pacote `Portfolio Integracoes SAP`.

**Objetivo:** demonstrar consumo de um **web service SOAP 1.1 via WSDL** com o **SOAP Adapter nativo do CPI** — protocolo ainda muito presente em integrações legadas (ERPs antigos, sistemas de terceiros) em projetos SAP.

Serviço usado: [`Calculator`](http://www.dneonline.com/calculator.asmx?WSDL) (dneonline.com), público e gratuito, só para fins de estudo.

## Desenho do iFlow

```
Sender --HTTPS--> Start → [Groovy: LerEntrada] → [Content Modifier: MontarEnvelope] → [Request-Reply] → [Groovy: MapearSOAP] → End
                                                                                             ↕ SOAP (Add)
                                                                                         Receiver (Calculator - dneonline.com)
```

| # | Passo | Detalhe |
|---|---|---|
| 1 | Sender HTTPS | Address `/http/soap/somar` · Body de entrada `{"a": 5, "b": 3}` |
| 2 | Groovy `LerEntrada` | [`src/LerEntrada.groovy`](src/LerEntrada.groovy) — lê o JSON e grava `a`/`b` como Exchange Properties (`numeroA`, `numeroB`) |
| 3 | Content Modifier `MontarEnvelope` | Body (Expression): `<tem:Add xmlns:tem="http://tempuri.org/"><tem:intA>${property.numeroA}</tem:intA><tem:intB>${property.numeroB}</tem:intB></tem:Add>` |
| 4 | Request-Reply + Receiver SOAP | Adapter Type **SOAP 1.x** · WSDL carregado de `calculator.asmx?WSDL` · Operation `Add` · Authentication **None** |
| 5 | Groovy `MapearSOAP` | [`src/MapearSOAP.groovy`](src/MapearSOAP.groovy) — extrai `<AddResult>` da resposta SOAP e devolve um JSON limpo |

## Armadilhas (e por que importam numa entrevista)

| Sintoma | Causa | Lição |
|---|---|---|
| `PayLoad elements cannot fit with message parts of BindingOperation` | O Content Modifier estava montando o `<soap:Envelope><soap:Body>` inteiro | O SOAP Adapter nativo do CPI (com WSDL + Operation selecionados) já monta o envelope sozinho — só o elemento da operação (`<tem:Add>`) deve ser enviado no body |
| `unable to resolve class groovy.xml.XmlSlurper` | Import `groovy.xml.XmlSlurper` | O runtime Groovy do CPI é uma versão mais antiga, onde `XmlSlurper` está em `groovy.util.XmlSlurper`, não em `groovy.xml.XmlSlurper` |
| Erro de autenticação na chamada ao Receiver | Authentication do Request-Reply configurada como **Client Certificate** (herdada de outro iFlow copiado) | Serviço público sem certificado exige Authentication **None** — mesmo tipo de erro já visto no HTTP Adapter do SuccessFactors |
| `XPath` não encontra `a`/`b` no Content Modifier de entrada | Tentativa de ler o body JSON com XPath (`LerEntrada` como Content Modifier) | JSON não é XML — a leitura do body de entrada precisa ser um **Groovy Script** com `JsonSlurper`, não um Content Modifier com XPath |

## Teste

`POST /http/soap/somar`, body `{"a": 5, "b": 3}` → **200**:
```json
{
  "operacao": "Add",
  "numeroA": 5,
  "numeroB": 3,
  "resultado": 8,
  "origem": "Calculator SOAP (www.dneonline.com) - servico publico de estudo"
}
```

Print: [`docs/img/28-postman-soap-200.png`](docs/img/28-postman-soap-200.png)
