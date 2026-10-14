/*
 * Testes locais dos scripts do iFlow (sem tenant CPI).
 * Como rodar (na raiz do repositório, com Groovy instalado):
 *   groovyc -d build test/mock/com/sap/gateway/ip/core/customdev/util/Message.groovy
 *   groovy -cp build test/RodarTestes.groovy
 */
import com.sap.gateway.ip.core.customdev.util.Message
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

println "\nResultado: $ok OK, $falha falha(s)\n"
println "Exemplo de resposta (cenário 1):\n" + m1.getBody()
if (falha > 0) System.exit(1)
