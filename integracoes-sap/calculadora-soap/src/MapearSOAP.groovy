/*
 * MapearSOAP.groovy
 * iFlow: Calculadora_SOAP (SAP Cloud Integration)
 *
 * Fica DEPOIS do Request-Reply (que chama o serviço SOAP). A resposta vem
 * como XML dentro de um envelope SOAP:
 *
 * <soap:Envelope ...>
 *   <soap:Body>
 *     <AddResponse xmlns="http://tempuri.org/">
 *       <AddResult>8</AddResult>
 *     </AddResponse>
 *   </soap:Body>
 * </soap:Envelope>
 *
 * Este script extrai o <AddResult> e devolve um JSON limpo, no mesmo padrão
 * dos outros endpoints do portfólio.
 */
import com.sap.gateway.ip.core.customdev.util.Message
import groovy.json.JsonOutput
import groovy.util.XmlSlurper

def Message processData(Message message) {
    def body = message.getBody(String) as String

    def xml
    try {
        xml = new XmlSlurper().parseText(body)
    } catch (Exception e) {
        throw new IllegalArgumentException("Resposta do serviço SOAP não é um XML válido: " + e.message)
    }

    // depthFirst() + find evita depender do prefixo de namespace exato
    // (soap:, soapenv:, s: variam conforme o cliente SOAP).
    def resultNode = xml.depthFirst().find { it.name() == 'AddResult' }
    if (resultNode == null) {
        throw new IllegalArgumentException("Campo 'AddResult' não encontrado na resposta SOAP.")
    }

    def numeroA = message.getProperty("numeroA")
    def numeroB = message.getProperty("numeroB")

    def resposta = [
        operacao  : "Add",
        numeroA   : numeroA as Integer,
        numeroB   : numeroB as Integer,
        resultado : resultNode.text() as Integer,
        origem    : "Calculator SOAP (www.dneonline.com) - servico publico de estudo"
    ]

    message.setBody(JsonOutput.prettyPrint(JsonOutput.toJson(resposta)))
    message.setHeader("Content-Type", "application/json")
    message.setHeader("CamelHttpResponseCode", 200)
    return message
}
