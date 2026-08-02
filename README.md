# 🚀 POC de Performance: FlatBuffers vs JSON

Este repositório é uma prova de conceito de performance para comparar o uso de FlatBuffers e JSON em comunicação entre serviços. A ideia central é medir e entender como uma API BFF em Java consegue consumir um serviço em Go usando ambos os formatos, mantendo a mesma payload e validando a diferença de custo de transporte e processamento.

## 🧭 Visão geral da arquitetura

A integração foi mapeada por engenharia reversa a partir do código dos dois projetos:

- O projeto `bff_java` é uma API em Spring Boot que funciona como BFF.
- O projeto `server_go` expõe endpoints em Go usando Gin.
- O Java consulta o Go via OpenFeign.
- O header `flow_type` define qual payload será consumido:
  - `json` → retorna JSON
  - `flatc` (ou qualquer valor diferente de `json`) → retorna FlatBuffers

Fluxo da integração:

```text
Cliente HTTP
   ↓
BFF Java (Spring Boot)
   ├─ Header: flow_type=json
   │    ↓
   │   Feign → GET http://localhost:8081/json
   │    ↓
   │   Go retorna JSON
   │
   └─ Header: flow_type=flatc
        ↓
        Feign → GET http://localhost:8081/flatbuffers
        ↓
        Go retorna bytes FlatBuffers
```

## 🔎 Engenharia reversa da integração

### 1) BFF Java

O projeto Java possui a seguinte cadeia de execução:

- `DemoApplication` ativa o Spring Boot e `@EnableFeignClients`
- `UserGateway` define o cliente Feign apontando para `http://localhost:8081`
- `UserController` expõe `GET /users`
- O header `flow_type` decide o fluxo a ser executado
- `Service` faz o dispatch:
  - `flow.equalsIgnoreCase("flatc") ? getFlatc() : getJson();`

Código relevante:

- `UserGateway.getJson()` → chama `GET /json`
- `UserGateway.getFlatc()` → chama `GET /flatbuffers` com `consumes = "application/x-flatbuffers"`
- `Service.getFlatc()` → decodifica a resposta em `byte[]` e converte com `User.getRootAsUser(buffer)`
- `Service.getJson()` → desserializa para `UserJson` e retorna o campo `name`

### 2) Serviço Go

O servidor Go expondo em `:8081` realiza:

- `GET /json` → responde com JSON usando `c.JSON(...)`
- `GET /flatbuffers` → monta uma estrutura `UserT`, empacota com FlatBuffers e devolve bytes em `application/x-flatbuffers`

A estrutura do schema usada no Go é definida em `user.fbs`:

```fbs
namespace com.bff.demo.model;

table User {
    name: string;
    age: byte;
}
```

Esse schema gera a estrutura de usuário usada no Java e no Go para representar os dados de forma compacta.

## 📁 Estrutura dos projetos

```text
poc-flatbuffers/
├─ bff_java/
│  ├─ src/main/java/com/bff/demo/
│  │  ├─ DemoApplication.java
│  │  ├─ Service.java
│  │  ├─ UserController.java
│  │  ├─ UserGateway.java
│  │  └─ model/
│  │     ├─ User.java
│  │     └─ UserJson.java
│  ├─ src/main/resources/
│  │  ├─ application.yaml
│  │  └─ user.fbs
│  └─ pom.xml
├─ server_go/
│  ├─ main.go
│  ├─ go.mod
│  └─ model/
│     └─ User.go
└─ README.md
```

## ✅ Como rodar o projeto

### Pré-requisitos

- Java 21+
- Maven (ou o wrapper do projeto `./mvnw`)
- Go 1.20+

### 1) Iniciar o serviço Go

```bash
cd server_go
go run main.go
```

O servidor vai subir em:

```text
http://localhost:8081
```

### 2) Iniciar o BFF Java

Em outro terminal:

```bash
cd bff_java
./mvnw spring-boot:run
```

O BFF vai subir em:

```text
http://localhost:8080
```

## 🚦 Teste de carga com k6

O repositório também contém o arquivo de teste de carga em [loadtest.js](loadtest.js). Ele simula requisições ao BFF em Java para medir latência e taxa de erro do endpoint `/users`.

### Instalar o k6

No macOS:

```bash
brew install k6
```

No Linux (Ubuntu/Debian):

```bash
sudo gpg -k
sudo gpg --no-default-keyring --keyring /usr/share/keyrings/k6-archive-keyring.gpg --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys C5AD17C747E9415F
echo 'deb [signed-by=/usr/share/keyrings/k6-archive-keyring.gpg] https://dl.k6.io/deb stable main' | sudo tee /etc/apt/sources.list.d/k6.list
sudo apt-get update
sudo apt-get install k6
```

### Executar o teste

Na raiz do projeto:

```bash
k6 run loadtest.js
```

Esse script faz chamadas para:

```text
http://127.0.0.1:8080/users
```

Ele usa o header `Content-Type: application/json` e também gera um `X-Correlation-ID` por requisição. Como o BFF usa o header `flow_type` com valor padrão `json`, o teste roda sobre o fluxo JSON por padrão.

Se quiser testar também o fluxo FlatBuffers no k6, basta ajustar o script para incluir o header:

```javascript
const headers = {
  'Content-Type': 'application/json',
  'flow_type': 'flatc',
  'X-Correlation-ID': uuidv4(),
};
```

E então rodar novamente:

```bash
k6 run loadtest.js
```

## 🧪 Exemplos de curl para testar o BFF

### JSON

```bash
curl -i \
  -H "flow_type: json" \
  http://localhost:8080/users
```

Resposta esperada:

```json
{"name":"John Doe"}
```

### FlatBuffers

```bash
curl -i \
  -H "flow_type: flatc" \
  http://localhost:8080/users
```

Como a resposta é binária, normalmente o curl vai mostrar bytes/brutos. Para gravar a resposta em arquivo:

Se quiser testar diretamente o endpoint do Go:

```bash
curl -i http://localhost:8081/json
curl -i http://localhost:8081/flatbuffers
```

## 📊 Por que esta POC existe?

Essa estrutura foi montada para comparar de forma prática duas estratégias de serialização:

- JSON: mais legível, fácil de debugar e interoperável
- FlatBuffers: mais compacto, rápido e eficiente para payloads grandes ou alto volume de trafego

A POC evidencia que a integração pode ser feita sem mudar a camada de API externa do BFF, apenas alternando o tipo de resposta no header `flow_type`.

## 🧪 Resultado do teste de stress com flatbuffers - 10ms mais rapido do que usando json
```bash
 █ THRESHOLDS 

    checks
    ✓ 'rate>0.95' rate=100.00%

    http_req_duration
    ✓ 'p(95)<1000' p(95)=2.77ms

    http_req_failed
    ✓ 'rate<0.05' rate=0.00%


  █ TOTAL RESULTS 

    checks_total.......: 16776   92.973534/s
    checks_succeeded...: 100.00% 16776 out of 16776
    checks_failed......: 0.00%   0 out of 16776

    ✓ status is 200
    ✓ not timed out

    HTTP
    http_req_duration..............: avg=1.55ms min=0s med=1.51ms max=29.46ms p(90)=2.25ms p(95)=2.77ms
      { expected_response:true }...: avg=1.55ms min=0s med=1.51ms max=29.46ms p(90)=2.25ms p(95)=2.77ms
    http_req_failed................: 0.00%  0 out of 8388
    http_reqs......................: 8388   46.486767/s

    EXECUTION
    iteration_duration.............: avg=1s     min=1s med=1s     max=1.02s   p(90)=1s     p(95)=1s    
    iterations.....................: 8388   46.486767/s
    vus............................: 2      min=1         max=99 
    vus_max........................: 100    min=100       max=100

    NETWORK
    data_received..................: 1.2 MB 6.7 kB/s
    data_sent......................: 1.5 MB 8.4 kB/s




running (3m00.4s), 000/100 VUs, 8388 complete and 0 interrupted iterations
```

## 🧪 Resultado do teste de stress com json
```bash
 THRESHOLDS 

    checks
    ✓ 'rate>0.95' rate=100.00%

    http_req_duration
    ✓ 'p(95)<1000' p(95)=2.85ms

    http_req_failed
    ✓ 'rate<0.05' rate=0.00%


  █ TOTAL RESULTS 

    checks_total.......: 16774   92.948843/s
    checks_succeeded...: 100.00% 16774 out of 16774
    checks_failed......: 0.00%   0 out of 16774

    ✓ status is 200
    ✓ not timed out

    HTTP
    http_req_duration..............: avg=1.65ms min=0s med=1.55ms max=29.21ms p(90)=2.36ms p(95)=2.85ms
      { expected_response:true }...: avg=1.65ms min=0s med=1.55ms max=29.21ms p(90)=2.36ms p(95)=2.85ms
    http_req_failed................: 0.00%  0 out of 8387
    http_reqs......................: 8387   46.474422/s

    EXECUTION
    iteration_duration.............: avg=1s     min=1s med=1s     max=1.03s   p(90)=1s     p(95)=1s    
    iterations.....................: 8387   46.474422/s
    vus............................: 2      min=1         max=99 
    vus_max........................: 100    min=100       max=100

    NETWORK
    data_received..................: 1.2 MB 6.7 kB/s
    data_sent......................: 1.4 MB 7.6 kB/s




running (3m00.5s), 000/100 VUs, 8387 complete and 0 interrupted iterations
```

## 💡 Observações finais

- O BFF Java atua como camada de adaptação entre cliente e backend.
- O serviço Go entrega dados em cada formato sem que o cliente final precise conhecer a implementação interna.
- O projeto é ótimo para estudar trade-offs de performance, payload e serialização em sistemas distribuídos.


