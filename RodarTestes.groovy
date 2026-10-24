/*
 * Testes locais dos scripts do iFlow (sem tenant CPI).
 * Como rodar (na raiz do repositório, com Groovy instalado):
 *   groovyc -d build $(find test/mock -name "*.groovy")
 *   groovy -cp build test/RodarTestes.groovy
 */
import com.sap.gateway.ip.core.customdev.util.Message
import com.sap.it.api.mapping.ValueMappingApi
import groovy.json.JsonSlurper

def raiz = new File(getClass().protectionDomain.codeSource.location.path).parentFile.parentFile
def shell = new GroovyShell(this.class.classLoader)
def calcula = shell.parse(new File(raiz, "src/scripts/CalculaCBSIBS.groovy"))
def trataErro = shell.parse(new File(raiz, "src/scripts/TrataErro.groovy"))

int ok = 0, falha = 0
def check = { String nome, boolean cond ->
    if (cond) { ok++; println "  [OK]    $nome" } else { falha++; println "  [FALHA] $nome" }
}
def novaMsg = { String arquivo ->
    def m = new Message()
    m.setBody(new File(raiz, "test/payloads/$arquivo").getText("UTF-8"))
    m.setProperty("aliquotaCBS", "0.9")
    m.setProperty("aliquotaIBS", "0.1")
    m
}
def simulaErro = { Message m, Closure passo ->
    try { passo(m); return null }
    catch (Exception e) { m.setProperty("CamelExceptionCaught", e); trataErro.processData(m); return m }
}

println "\nCenário 1: documento válido"
def m1 = calcula.processData(novaMsg("request_ok.json"))
def r1 = new JsonSlurper().parseText(m1.getBody())
// Item 1: 2 x 4500 - 200 = 8800 | CBS 0,9% = 79,20 | IBS 0,1% = 8,80
// Item 2: 3 x 389,90 = 1169,70   | CBS = 10,53     | IBS = 1,17
check("base item 1 = 8800.00", r1.itens[0].baseCalculo == 8800.00)
check("CBS item 1 = 79.20", r1.itens[0].valorCBS == 79.20)
check("IBS item 2 = 1.17", r1.itens[1].valorIBS == 1.17)
check("base total = 9969.70", r1.totais.baseCalculo == 9969.70)
check("CBS total = 89.73", r1.totais.valorCBS == 89.73)
check("IBS total = 9.97", r1.totais.valorIBS == 9.97)
check("total tributos = 99.70", r1.totais.valorTotalTributos == 99.70)

println "\nCenário 2: NCM inválido -> HTTP 400"
def m2 = simulaErro(novaMsg("request_erro_ncm.json")) { calcula.processData(it) }
check("caiu no tratamento de erro", m2 != null)
check("status 400", m2?.getHeaders()?.get("CamelHttpResponseCode") == 400)
check("mensagem cita o NCM", new JsonSlurper().parseText(m2.getBody()).mensagem.contains("ncm"))

println "\nCenário 3: alíquota não configurada -> HTTP 500"
def m3base = novaMsg("request_ok.json"); m3base.getProperties().remove("aliquotaCBS")
def m3 = simulaErro(m3base) { calcula.processData(it) }
check("status 500", m3?.getHeaders()?.get("CamelHttpResponseCode") == 500)

println "\nCenário 4: JSON malformado -> HTTP 400"
def m4base = new Message(); m4base.setBody("{ isto nao e json"); m4base.setProperty("aliquotaCBS", "0.9"); m4base.setProperty("aliquotaIBS", "0.1")
def m4 = simulaErro(m4base) { calcula.processData(it) }
check("status 400", m4?.getHeaders()?.get("CamelHttpResponseCode") == 400)

println "\nCenário 5: NF-e XML convertida pelo XSLT -> mesmo total"
def m5 = calcula.processData(novaMsg("nfe_convertido_esperado.json"))
def r5 = new JsonSlurper().parseText(m5.getBody())
check("chave de acesso preservada", r5.documento.chaveAcesso == "35261012345678000195550010000001231000001230")
check("total tributos = 99.70", r5.totais.valorTotalTributos == 99.70)

println "\nCenário 6: XML sem infNFe (XSLT gera JSON vazio) -> HTTP 400"
def m6base = new Message(); m6base.setBody('{ "documento": { }, "itens": [ ] }'); m6base.setProperty("aliquotaCBS", "0.9"); m6base.setProperty("aliquotaIBS", "0.1")
def m6 = simulaErro(m6base) { calcula.processData(it) }
check("status 400", m6?.getHeaders()?.get("CamelHttpResponseCode") == 400)

println "\nCenário 7: Value Mapping por NCM (medicamento com alíquota reduzida)"
ValueMappingApi.tabela = [
    "SIMULADOR|NCM|84713012|CBS|ALIQUOTA": "0.9",  "SIMULADOR|NCM|84713012|IBS|ALIQUOTA": "0.1",
    "SIMULADOR|NCM|30049099|CBS|ALIQUOTA": "0.36", "SIMULADOR|NCM|30049099|IBS|ALIQUOTA": "0.04"
]
def m7 = calcula.processData(novaMsg("request_value_mapping.json"))
def r7 = new JsonSlurper().parseText(m7.getBody())
// Item 3: 10 x 25,00 = 250,00 | CBS 0,36% = 0,90 | IBS 0,04% = 0,10
check("item 3 usa Value Mapping", r7.itens[2].origemAliquota == "VALUE_MAPPING")
check("item 2 (sem mapeamento) usa padrão", r7.itens[1].origemAliquota == "PADRAO")
check("CBS item 3 = 0.90", r7.itens[2].valorCBS == 0.90)
check("total CBS = 90.63", r7.totais.valorCBS == 90.63)
check("total tributos = 100.70", r7.totais.valorTotalTributos == 100.70)
new File(raiz, "test/payloads/response_value_mapping_exemplo.json").setText(m7.getBody(), "UTF-8")

println "\nCenário 8: Value Mapping com valor inválido -> HTTP 500"
ValueMappingApi.tabela = ["SIMULADOR|NCM|84713012|CBS|ALIQUOTA": "abc"]
def m8 = simulaErro(novaMsg("request_ok.json")) { calcula.processData(it) }
check("status 500", m8?.getHeaders()?.get("CamelHttpResponseCode") == 500)
ValueMappingApi.tabela = [:]

println "\nCenário 9: ID da simulação para o Data Store"
def m9 = calcula.processData(novaMsg("request_ok.json"))
def id9 = m9.getProperty("idSimulacao")
check("property idSimulacao no formato SIM-<numero>-<timestamp>", id9 ==~ /SIM-000123-\d{17}/)
check("mesmo ID no JSON de resposta", new JsonSlurper().parseText(m9.getBody()).idSimulacao == id9)

println "\nCenário 10: XML reprovado no XML Validator (XSD) -> HTTP 400"
def m10 = new Message()
m10.setProperty("CamelExceptionCaught", new RuntimeException("Validation failed",
    new Exception("cvc-pattern-valid: Value '8517627' is not facet-valid with respect to pattern '[0-9]{2}|[0-9]{8}' for type 'TNcm'.")))
trataErro.processData(m10)
def r10 = new JsonSlurper().parseText(m10.getBody())
check("status 400", m10.getHeaders().get("CamelHttpResponseCode") == 400)
check("erro XML_INVALIDO", r10.erro == "XML_INVALIDO")
check("mensagem cita o NCM", r10.mensagem.contains("8517627"))

println "\nResultado: $ok OK, $falha falha(s)\n"
println "Exemplo de resposta (cenário 1):\n" + m1.getBody()
if (falha > 0) System.exit(1)
