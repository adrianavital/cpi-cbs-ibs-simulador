# Passo a passo: construir o iFlow no SAP Integration Suite (trial)

Tempo estimado: 2 a 3 horas na primeira vez.

Ao final você terá: o iFlow rodando no tenant, testado pelo Postman, com prints e o `.zip` exportado neste repositório.

---

## Etapa 1 – Ambiente

| # | Ação | Onde |
|---|------|------|
| 1 | Acessar a conta trial do BTP | https://account.hanatrial.ondemand.com |
| 2 | Abrir o subaccount trial → **Services → Service Marketplace** → *Integration Suite* → **Create** (plano *trial*) | BTP Cockpit |
| 3 | Em **Security → Users**, atribuir ao seu usuário o role collection `Integration_Provisioner` | BTP Cockpit |
| 4 | Abrir o Integration Suite → **Manage Capabilities → Add** → *Build Integration Scenarios* | Integration Suite |
| 5 | Atribuir ao seu usuário os role collections `PI_Administrator`, `PI_Business_Expert` e `PI_Integration_Developer` | BTP Cockpit |

Feito isso, faça logout e login para os roles valerem.

> Atalho: na página inicial do BTP Cockpit, o **Booster** *Set up Integration Suite Trial* faz os passos 2 e 3 automaticamente. Os nomes de menus mudam com as versões do BTP; se algo estiver diferente, procure pelo nome do serviço.

---

## Etapa 2 – Criar o pacote e o iFlow

1. **Design → Integrations and APIs → Create**
   - Name: `Portfolio Reforma Tributaria`
   - Short description: `Simulação de cálculo de CBS e IBS via REST`
2. Dentro do pacote: **Artifacts → Add → Integration Flow**
   - Name: `CBS_IBS_Simulador`

---

## Etapa 3 – Montar o fluxo

Desenho final:

```
Sender ──HTTPS──▶ Start ─▶ [Content Modifier: Parametros] ─▶ [Groovy: CalculaCBSIBS] ─▶ End

Exception Subprocess:  Error Start ─▶ [Groovy: TrataErro] ─▶ End
```

### 3.1 Sender (HTTPS)
- Ligue o *Sender* ao *Start* → adapter **HTTPS**.
- **Address:** `/cbsibs/simular`
- **Authorization:** User Role → `ESBMessaging.send`
- **CSRF Protected:** desmarcado (facilita o teste no Postman)

### 3.2 Content Modifier "Parametros"
Aba **Exchange Property**, adicionar:

| Name | Source Type | Source Value |
|---|---|---|
| `aliquotaCBS` | Constant | `{{ALIQUOTA_CBS}}` |
| `aliquotaIBS` | Constant | `{{ALIQUOTA_IBS}}` |
| `logPayload` | Constant | `{{LOG_PAYLOAD}}` |

As chaves duplas `{{...}}` **externalizam** o parâmetro, que depois é configurado sem editar o iFlow. Na entrevista, este é um ponto forte: mostra boa prática.

### 3.3 Groovy Script "CalculaCBSIBS"
- Adicionar **Script → Groovy Script** após o Content Modifier.
- Clicar em **Create**, apagar o código de exemplo do editor e colar **todo o código** do arquivo [`src/scripts/CalculaCBSIBS.groovy`](../src/scripts/CalculaCBSIBS.groovy). Abra o arquivo num editor de texto, use Ctrl+A e Ctrl+C. Não cole o caminho do arquivo: o editor precisa começar com `/*` e ter `def Message processData`.

### 3.4 Exception Subprocess
- Paleta → **Process → Exception Subprocess**, arrastar para dentro do Integration Process.
- Dentro dele, entre *Error Start* e *End*: **Groovy Script** com **todo o código** do arquivo [`src/scripts/TrataErro.groovy`](../src/scripts/TrataErro.groovy), pelo mesmo procedimento.
- O evento final do subprocess precisa ser **End Message** (ícone de envelope), para a resposta de erro voltar ao chamador.

**Save** e depois **Save as version** (`1.0.0`).

---

## Etapa 4 – Configurar e fazer o deploy

1. Botão **Configure** → aba **More**:
   - `ALIQUOTA_CBS` = `0.9`
   - `ALIQUOTA_IBS` = `0.1`
   - `LOG_PAYLOAD` = `true`
2. **Deploy**.
3. **Monitor → Manage Integration Content** → status *Started* → copiar o **Endpoint**.

> As alíquotas acima são valores de exemplo inspirados na fase de teste de 2026 (LC 214/2025). Confirme a legislação vigente antes de usar qualquer valor fora deste estudo.

---

## Etapa 5 – Credencial para chamar o iFlow

1. BTP Cockpit → **Service Marketplace** → *Process Integration Runtime* → **Create**
   - Plan: `integration-flow`
   - Parameters (JSON):
     ```json
     { "roles": ["ESBMessaging.send"], "grant-types": ["client_credentials"] }
     ```
2. Na instância criada → **Create Service Key**.
3. Anote `clientid`, `clientsecret` e `tokenurl`.

---

## Etapa 6 – Testar no Postman

| Campo | Valor |
|---|---|
| Método | `POST` |
| URL | o Endpoint copiado na Etapa 4 |
| Authorization (mais simples) | **Basic Auth** → Username = `clientid`, Password = `clientsecret` |
| Authorization (alternativa) | OAuth 2.0 → Client Credentials → Access Token URL = `tokenurl` → **Get New Access Token** → **Use Token** |
| Header | apenas `Content-Type: application/json` |
| Body | **raw → JSON**, colando o JSON que está dentro do arquivo `test/payloads/request_ok.json` |

> Atenção:
> - `grant-types` **não** é header. Ele só entra no JSON de criação da instância (Etapa 5).
> - No Body vai o JSON em si (começa com `{`), e não o nome do arquivo.
> - Use a service key da instância *Process Integration Runtime* com plano `integration-flow`: o `clientid` termina em `|it!b...`.
> - **401** = credencial errada ou ausente. **403** = falta o role `ESBMessaging.send` na instância. **404** = endpoint errado ou iFlow não implantado.

**Resultados esperados:**

| Teste | Payload | Resposta |
|---|---|---|
| Sucesso | `request_ok.json` | HTTP 200, total de tributos `99.70` |
| NCM inválido | `request_erro_ncm.json` | HTTP 400, `REQUISICAO_INVALIDA` |
| JSON quebrado | `{ abc` | HTTP 400 |

Depois, em **Monitor → Monitor Message Processing**, abra a mensagem e veja os anexos *Entrada* e *Saida* (gerados porque `LOG_PAYLOAD=true`).

---

## Etapa 7 – Evidências para o portfólio

Salve em `docs/img/`:

| Arquivo | O que mostrar |
|---|---|
| `01-iflow.png` | O desenho completo do iFlow |
| `02-parametros.png` | Tela Configure com os parâmetros externalizados |
| `03-postman-200.png` | Postman com a resposta de sucesso |
| `04-postman-400.png` | Postman com o erro de NCM |
| `05-monitor.png` | Message Processing com os anexos |

Por último, exporte o iFlow: no pacote, menu **Actions → Download** do artefato. Coloque o `.zip` na pasta `iflow/`.

**Antes de publicar:** apague dos prints qualquer `clientsecret`, token ou URL do tenant.
