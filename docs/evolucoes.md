# Evoluções do Simulador CBS/IBS

Pré-requisito: o iFlow base `CBS_IBS_Simulador` funcionando ([passo-a-passo-iflow.md](passo-a-passo-iflow.md)).

---

## Evolução 1 – Receber o XML da NF-e (XSLT)

**Objetivo:** novo endpoint `/cbsibs/nfe` que recebe o XML da NF-e, converte para o JSON do simulador e reutiliza o mesmo cálculo.

| # | Passo | Detalhe |
|---|---|---|
| 1 | Copiar o iFlow | Artifacts → `CBS_IBS_Simulador` → Actions → **Copy** → `CBS_IBS_Simulador_NFe` |
| 2 | Mudar o endereço | Conexão HTTPS → Address `/cbsibs/nfe` (precisa ser único no tenant) |
| 3 | Inserir o XSLT | Paleta → Mapping → **XSLT Mapping** sobre a seta entre *Receber Documento* e *Definir Alíquotas* → Processing → Upload de [`NFe_para_JSON.xsl`](../src/mappings/NFe_para_JSON.xsl) |
| 4 | Implantar | Save → Configure (alíquotas) → Deploy |
| 5 | Testar | `POST /cbsibs/nfe`, `Content-Type: application/xml`, body = [`nfe_exemplo.xml`](../test/payloads/nfe_exemplo.xml) → **200**, total **99,70** |

| Teste | Resultado esperado |
|---|---|
| NF-e válida | 200, `chaveAcesso` preservada |
| XML que não é NF-e | 400 – `documento.numero` ausente |
| XML quebrado | 500 – falha de parse no XSLT, antes do Groovy |

> Alerta do CPI *"Http Sender may not pass XML message to XSLT Mapping"*: esperado. O HTTPS aceita qualquer formato; o contrato do endpoint é XML. Melhoria: Router validando `Content-Type` → 415.

---

## Evolução 2 – Alíquotas por NCM (Value Mapping)

**Objetivo:** alíquota específica por NCM, mantida pela área fiscal sem reimplantar o iFlow.

| # | Passo | Detalhe |
|---|---|---|
| 1 | Criar o Value Mapping | Artifacts → Add → **Value Mapping** → `VM_Aliquotas_NCM` |
| 2 | Par CBS | Agency `SIMULADOR` / Identifier `NCM` ⇄ Agency `CBS` / Identifier `ALIQUOTA` |
| 3 | Valores CBS | `84713012` → `0.9` · `30049099` → `0.36` |
| 4 | Par IBS | Agency `SIMULADOR` / Identifier `NCM` ⇄ Agency `IBS` / Identifier `ALIQUOTA` |
| 5 | Valores IBS | `84713012` → `0.1` · `30049099` → `0.04` |
| 6 | Implantar o Value Mapping | Save → **Deploy** (separado do iFlow) |
| 7 | Atualizar o script | Colar o novo [`CalculaCBSIBS.groovy`](../src/scripts/CalculaCBSIBS.groovy) nos 2 iFlows → Save → Deploy |
| 8 | Testar | `POST /cbsibs/simular` com [`request_value_mapping.json`](../test/payloads/request_value_mapping.json) → **200**, total **100,70** |

O script consulta:
```groovy
ITApiFactory.getApi(ValueMappingApi.class, null)
    .getMappedValue("SIMULADOR", "NCM", ncm, "CBS", "ALIQUOTA")
```
NCM não mapeado → alíquota padrão (`"origemAliquota": "PADRAO"`).

> Valores didáticos, inspirados na redução de 60% da LC 214 para alguns medicamentos (0,9 × 0,4 = 0,36).

---

## Evolução 3 – Histórico no Data Store

**Objetivo:** gravar cada simulação com chave de negócio e consultá-la por ID.

### Parte A – Gravar
| # | Passo | Detalhe |
|---|---|---|
| 1 | Atualizar o script | Versão que gera `idSimulacao` (`SIM-<numero>-<yyyyMMddHHmmssSSS>`) |
| 2 | Inserir Data Store Write | Paleta → Persistence → Data Store Operations → **Write**, entre *Validar e Calcular* e *Retornar Resultado* |
| 3 | Configurar | Name `HistoricoSimulacoes` · Visibility **Global** · Entry ID `${property.idSimulacao}` · Alerta 2 d · Expiração 30 d · **Encrypt** ✅ · Overwrite ☐ |
| 4 | Implantar e testar | Resposta traz `"idSimulacao"`; Monitor → Manage Stores → **Data Stores** mostra a entrada |

### Parte B – Consultar
| # | Passo | Detalhe |
|---|---|---|
| 1 | Novo iFlow | `CBS_IBS_Historico`, HTTPS `/cbsibs/historico` |
| 2 | Liberar o header | Runtime Configuration → **Allowed Header(s):** `simulacaoId` |
| 3 | Data Store Get | `HistoricoSimulacoes` · Global · Entry ID `${header.simulacaoId}` · Throw Exception on Missing Entry ✅ |
| 4 | Testar | `GET /cbsibs/historico`, header `simulacaoId: SIM-...`, **sem body** → **200** com a simulação |

> Entradas gravadas antes do script gerar o ID recebem UUID automático do CPI e não podem ser consultadas: por isso a chave de negócio.
