# Simulador CBS/IBS – SAP Cloud Integration (CPI)

Integração de estudo no **SAP Integration Suite** que recebe documentos fiscais via REST (**JSON ou XML da NF-e**), valida os itens e calcula **CBS** e **IBS** (Reforma Tributária – LC 214/2025), com **alíquotas por NCM** mantidas em Value Mapping e **histórico** de cada simulação gravado no Data Store.

> **Aviso:** projeto de portfólio para estudo. A base de cálculo é simplificada e as alíquotas são didáticas; não substitui o cálculo fiscal oficial.

*English summary: SAP CPI integration that receives invoices via REST (JSON or Brazilian NF-e XML), validates them and simulates the new Brazilian consumption taxes (CBS/IBS). Features XSLT mapping, NCM-based rates via Value Mapping, Data Store history with lookup API, externalized parameters and standardized error handling.*

---

## Arquitetura

```mermaid
flowchart LR
    A[Cliente REST<br/>Postman / ERP] -- "POST JSON" --> B1[HTTPS<br/>/cbsibs/simular]
    A -- "POST XML NF-e" --> B2[HTTPS<br/>/cbsibs/nfe]
    A -- "GET + header simulacaoId" --> B3[HTTPS<br/>/cbsibs/historico]
    subgraph CPI["SAP Cloud Integration"]
        B2 --> X[XSLT<br/>NF-e → JSON]
        X --> C[Content Modifier<br/>alíquotas padrão]
        B1 --> C
        C --> D[Groovy<br/>CalculaCBSIBS]
        VM[(Value Mapping<br/>alíquota por NCM)] -.-> D
        D --> W[Data Store Write<br/>HistoricoSimulacoes]
        W --> E[Resposta JSON<br/>HTTP 200]
        B3 --> G[Data Store Get]
        W -.-> G
        ERR[Exception Subprocess<br/>Groovy TrataErro] -.-> F[Erro JSON<br/>HTTP 400 / 500]
    end
```

| Artefato no CPI | Tipo | Endpoint / uso |
|---|---|---|
| `CBS_IBS_Simulador` | Integration Flow | `POST /cbsibs/simular` (JSON) + grava histórico |
| `CBS_IBS_Simulador_NFe` | Integration Flow | `POST /cbsibs/nfe` (XML da NF-e) |
| `CBS_IBS_Historico` | Integration Flow | `GET /cbsibs/historico` (header `simulacaoId`) |
| `VM_Aliquotas_NCM` | Value Mapping | Alíquotas CBS/IBS por NCM |

## O que este projeto demonstra

| Competência | Onde |
|---|---|
| HTTPS sender adapter com autenticação por client credentials | Todos os iFlows |
| Parâmetros externalizados (`{{ALIQUOTA_CBS}}`) | Content Modifier |
| Groovy: parse de JSON, validação, `BigDecimal`, arredondamento fiscal | `src/scripts/CalculaCBSIBS.groovy` |
| **XSLT 2.0** com namespace da NF-e, gerando JSON | `src/mappings/NFe_para_JSON.xsl` |
| **Value Mapping** consultado via `ValueMappingApi` com fallback para o padrão | `CalculaCBSIBS.groovy` + `VM_Aliquotas_NCM` |
| **Data Store** Write/Get com chave de negócio, retenção e criptografia | `CBS_IBS_Simulador` e `CBS_IBS_Historico` |
| Tratamento de erro padronizado (400 x 500) | `src/scripts/TrataErro.groovy` |
| Log de payload controlado por parâmetro (memória e LGPD) | parâmetro `LOG_PAYLOAD` |
| Testes locais dos scripts e do XSLT, sem tenant | `test/RodarTestes.groovy` (23 verificações) |

## Exemplos

**JSON** (`test/payloads/request_value_mapping.json`): 3 itens, um deles com alíquota reduzida via Value Mapping.
```json
"itens": [
  { "item": 1, "ncm": "84713012", "baseCalculo": 8800.00, "aliquotaCBS": 0.9,  "origemAliquota": "VALUE_MAPPING", "valorCBS": 79.20 },
  { "item": 2, "ncm": "85176277", "baseCalculo": 1169.70, "aliquotaCBS": 0.9,  "origemAliquota": "PADRAO",        "valorCBS": 10.53 },
  { "item": 3, "ncm": "30049099", "baseCalculo": 250.00,  "aliquotaCBS": 0.36, "origemAliquota": "VALUE_MAPPING", "valorCBS": 0.90 }
],
"totais": { "baseCalculo": 10219.70, "valorCBS": 90.63, "valorIBS": 10.07, "valorTotalTributos": 100.70 }
```

**XML da NF-e** (`test/payloads/nfe_exemplo.xml`, layout 4.00 fictício) → mesmo cálculo, com a chave de acesso preservada:
```json
"documento": { "chaveAcesso": "35261012345678000195550010000001231000001230", "numero": "123", "ufOrigem": "SP", "ufDestino": "RJ" }
```

**Histórico:** cada resposta traz um `idSimulacao` (ex.: `SIM-000125-20261021193344938`), que é a chave no Data Store e pode ser consultada depois em `GET /cbsibs/historico`.

**Erro de validação** (HTTP 400):
```json
{ "status": 400, "erro": "REQUISICAO_INVALIDA", "mensagem": "itens[0].ncm deve ter 8 dígitos numéricos (recebido: '8471')." }
```

## Regras de cálculo

| Regra | Implementação |
|---|---|
| Base de cálculo do item | quantidade × valor unitário − desconto |
| Alíquota do item | Value Mapping por NCM; se não houver, alíquota padrão do Content Modifier |
| CBS / IBS do item | base × alíquota ÷ 100, arredondado em 2 casas (HALF_UP) |
| Totais | soma dos valores já arredondados por item |
| Validações | documento.numero obrigatório; ao menos 1 item; NCM com 8 dígitos; quantidade > 0; valores ≥ 0; desconto ≤ valor do item |

## Decisões técnicas

| Decisão | Motivo |
|---|---|
| XSLT em vez de Message Mapping | Saída em JSON e XSD da NF-e grande e com namespace; o XSLT é texto, versionável e testável localmente |
| Endpoint separado para XML | Não altera o iFlow JSON que já estava em produção |
| Value Mapping em vez de parâmetro | Tabela mantida pela área fiscal sem alterar nem reimplantar o iFlow |
| Chave de negócio no Data Store | ID gerado (`SIM-<numero>-<timestamp>`) permite consulta; sem ele o CPI gera UUID impossível de rastrear |
| Data Store com expiração de 30 dias e criptografia | Histórico curto e LGPD; para longo prazo, usar banco (HANA Cloud) |

## Estrutura

```
├── src/scripts/                 Scripts Groovy (cálculo e tratamento de erro)
├── src/mappings/                XSLT NF-e → JSON
├── test/payloads/               JSON e XML de teste (sucesso e erro)
├── test/mock/                   Mocks das APIs do CPI (Message, ValueMappingApi)
├── test/RodarTestes.groovy      Testes locais (23 verificações)
├── docs/passo-a-passo-iflow.md  Como montar o iFlow base no trial
├── docs/evolucoes.md            Como montar as 3 evoluções
├── docs/img/                    Prints do tenant
└── iflow/                       Exports (.zip) dos artefatos do CPI
```

## Como rodar os testes localmente

Requer Groovy 3 ou superior:
```bash
groovyc -d build $(find test/mock -name "*.groovy")
groovy -cp build test/RodarTestes.groovy
```

## Evidências (tenant SAP BTP trial)

### Versão base – JSON

**iFlow**
![iFlow CBS_IBS_Simulador](docs/img/01-iflow.png)

**Parâmetros externalizados**
![Parâmetros](docs/img/02-parametros.png)

**Sucesso – HTTP 200, total de tributos R$ 99,70**
![Postman 200](docs/img/03-postman-200.png)

**Erro de validação – HTTP 400**
![Postman 400](docs/img/04-postman-400.png)

**Monitoramento – anexos de entrada e saída**
| Entrada | Saída |
|---|---|
| ![Monitor entrada](docs/img/05-monitor.png) | ![Monitor saída](docs/img/06-monitor-saida.png) |

### Evolução 1 – XML da NF-e com XSLT

![iFlow NF-e](docs/img/07-iflow-nfe.png)
![Postman NF-e 200](docs/img/08-postman-nfe-200.png)

### Evolução 2 – Alíquotas por NCM com Value Mapping

![Value Mapping](docs/img/09-value-mapping.png)
![Postman Value Mapping](docs/img/10-postman-value-mapping.png)

### Evolução 3 – Histórico no Data Store

![iFlow com Data Store](docs/img/11-iflow-datastore.png)
![Data Store](docs/img/12-datastore-entrada.png)
![Consulta do histórico](docs/img/13-postman-historico.png)

## Roadmap

- [x] Receber o XML da NF-e e converter para JSON (XSLT)
- [x] Alíquotas por NCM via Value Mapping
- [x] Gravar o histórico de simulações no Data Store e consultar por ID
- [ ] Router validando `Content-Type` no endpoint NF-e (HTTP 415)
- [ ] Exception Subprocess no iFlow de histórico (HTTP 404 para ID inexistente)
- [ ] Publicar as APIs no SAP API Management com policy de rate limit

## Autora

**Adriana Vital dos Santos** – Consultora SAP Integration Suite (CPI) | SAP Certified Integration Developer
[LinkedIn](https://www.linkedin.com/in/adrianavsantos/)
