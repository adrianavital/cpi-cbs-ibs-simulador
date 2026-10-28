# Integração OData com SAP SuccessFactors (Employee Central)

iFlow `SF_EmployeeCentral_OData`, pacote `Portfolio Integracoes SAP SuccessFactors`.

**Objetivo:** demonstrar consumo de uma API **OData V2 real** do SuccessFactors (via sandbox do [SAP Business Accelerator Hub](https://api.sap.com)), um dos conectores mais comuns em projetos de integração de RH com SAP CPI.

## Por que HTTP Adapter e não OData Adapter

A API usa **Basic Authentication** no rótulo, mas na prática a sandbox exige um **header customizado `APIKey`** — o OData Adapter do CPI não tem um tipo de credencial para header customizado. Por isso o consumo é feito com o **HTTP Adapter genérico**, mesmo sendo uma API OData.

## Desenho do iFlow

```
Sender --HTTPS--> Start → [Content Modifier: HeadersSF] → [Request-Reply] → [Groovy: MapearSF] → End
                                                                  ↕ HTTP (GET)
                                                              Receiver (SuccessFactors sandbox)
```

| # | Passo | Detalhe |
|---|---|---|
| 1 | Sender HTTPS | Address `/sf/background-community` |
| 2 | Content Modifier `HeadersSF` | Aba **Message Header** (não Exchange Property): `APIKey` = chave da sandbox · `DataServiceVersion` = `2.0` · `Accept` = `application/json` |
| 3 | Request-Reply + Receiver HTTP | Address `https://sandbox.api.sap.com/successfactorsfoundation/odata/v2/Background_Community` · Method **GET** · Authentication **None** (a chave já vai no header) |
| 4 | **Request Headers** (aba Connection → Header Details) | `APIKey,DataServiceVersion,Accept,traceparent` — sem isso, o HTTP Adapter descarta os headers customizados antes de sair |
| 5 | Groovy `MapearSF` | [`src/MapearSF.groovy`](src/MapearSF.groovy) — extrai `d.results[]` (padrão OData V2) e devolve um JSON limpo |

## Armadilhas (e por que importam numa entrevista)

| Sintoma | Causa | Lição |
|---|---|---|
| `401 Unauthorized` | Campo **Request Headers** só liberava `traceparent`; o `APIKey` era descartado antes de sair | O HTTP Adapter do CPI tem uma lista de permissão de headers de saída — headers customizados precisam ser explicitamente liberados |
| `401` mesmo depois de liberar o header | O valor do header `APIKey` estava vazio (`<Define Value>` no Content Modifier) | Sempre conferir se o Source Value de um header foi realmente preenchido, não só o nome do campo |
| `404 Not Found` | Query string (`$top=20`) concatenada no Address sem o `?` | O HTTP Adapter do CPI não aceita `?` dentro de Address; a forma mais simples foi incluir `?$top=20` direto no Address (sem usar o campo Query separado) |

## Teste

`GET /sf/background-community` (mesma autenticação Basic Auth do CPI usada nos outros iFlows) → **200**, JSON com a lista de registros da entidade `Background_Community` (dados de sandbox da SAP, fictícios).

Print: [`docs/img/27-postman-sf-odata-200.png`](docs/img/27-postman-sf-odata-200.png)
