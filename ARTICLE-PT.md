# Testei o HttpClient do Java 26 com HTTP/3 em um agregador de viagens. E isso é o que realmente importa

Se você trabalha com sistemas backend que dependem de APIs externas, já sabe que o problema nem sempre está no seu código.

Às vezes, o gargalo real está no caminho entre o seu serviço e os sistemas de terceiros.

É por isso que o suporte a HTTP/3 no `HttpClient` do Java 26 é realmente interessante. Não porque “suporte a novo protocolo” fica bonito em release note, mas porque isso pode mudar o comportamento de aplicações Java que dependem de múltiplos serviços remotos sob pressão de latência.

Para não ficar só na teoria, eu montei uma aplicação demo com um cenário de agregador de viagens. O projeto está aqui:

https://github.com/dellamas/java26-http3-travel-aggregator-demo

A ideia foi simular um backend que consulta múltiplos fornecedores em paralelo para consolidar opções de hospedagem, algo bem mais próximo do mundo real do que um exemplo genérico de chamada HTTP isolada.

Depois de analisar as mudanças do `HttpClient` no Java 26, o suporte a HTTP/3 e montar esse exemplo, a conclusão mais importante foi simples:

HTTP/3 importa, mas o protocolo em si não é a história completa. O que realmente importa é como você decide usar isso.

## Um contexto mais realista

Vamos deixar os exemplos de brinquedo de lado.

Imagine uma API de agregação de viagens. O cliente faz uma busca por hotéis em Gramado, e o seu serviço precisa consultar múltiplos provedores antes de devolver a resposta final.

Uma única requisição pode disparar chamadas para:

- uma API de fornecedores de hospedagem
- um serviço de precificação
- uma API de avaliações
- um serviço de cupons ou fidelidade

Esse tipo de fluxo é comum em marketplaces, plataformas de reserva, integrações financeiras e sistemas logísticos.

Nesse cenário, pequenos atrasos se acumulam rápido. Uma única chamada externa mais lenta pode puxar para baixo o tempo total de resposta. E quando a rede está instável, comportamento de fallback e recuperação passam a importar ainda mais.

É aqui que HTTP/3 começa a ficar interessante.

## O que mudou no Java 26

O Java 26 adicionou suporte a HTTP/3 no `java.net.http.HttpClient`.

Isso significa que agora você pode preferir explicitamente `HTTP_3` ao construir um client ou uma request.

Um exemplo simples fica assim:

```java
HttpClient client = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_3)
        .build();

HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create("https://api.parceiro-viagens.com/hoteis/busca"))
        .header("Accept", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString("""
                {
                  \"destino\": \"Gramado\",
                  \"checkIn\": \"2026-04-10\",
                  \"checkOut\": \"2026-04-13\",
                  \"hospedes\": 2
                }
                """))
        .build();

HttpResponse<String> response =
        client.send(request, HttpResponse.BodyHandlers.ofString());

System.out.println(response.statusCode());
System.out.println(response.version());
```

Essa é a parte fácil.

A parte mais importante é decidir como sua aplicação deve se comportar quando o serviço remoto não suporta HTTP/3, ou quando suporta, mas nem sempre essa é a melhor escolha.

## Um exemplo com código mais próximo de aplicação real

Na demo que eu subi no GitHub, a aplicação usa `HttpClient`, virtual threads, timeouts explícitos e um fan out simples para múltiplos fornecedores.

O coração do exemplo é este:

```java
HttpClient client = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_2)
        .connectTimeout(Duration.ofSeconds(2))
        .build();

List<URI> fornecedores = List.of(
        URI.create("https://api.fornecedor-a.com/hoteis/busca"),
        URI.create("https://api.fornecedor-b.com/hoteis/busca"),
        URI.create("https://api.fornecedor-c.com/hoteis/busca")
);

try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    List<Future<String>> futures = fornecedores.stream()
            .map(uri -> executor.submit(() -> {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(uri)
                        .timeout(Duration.ofSeconds(3))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(payload))
                        .build();

                HttpResponse<String> response =
                        client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() >= 400) {
                    throw new IllegalStateException("Fornecedor falhou: " + uri);
                }

                return response.body();
            }))
            .toList();

    return futures.stream().map(Future::get).toList();
}
```

Na demo, eu deixei `HTTP_2` por padrão para manter o projeto estável e executável em ambiente comum. Mas o ponto do artigo continua valendo: quando você estiver em Java 26 com suporte a HTTP/3 disponível, a decisão relevante não é só trocar a versão do protocolo. É medir o impacto em um cenário real.

## Por que HTTP/3 interessa para backend

Do ponto de vista de funcionalidade, HTTP/3 não parece revolucionário quando comparado com HTTP/2.

A grande diferença está por baixo.

HTTP/2 roda sobre TCP.
HTTP/3 roda sobre QUIC, que usa UDP.

Essa mudança afeta comportamento de conexão, recuperação e performance, principalmente em cenários com perda de pacote, instabilidade de rede ou reconexões frequentes.

Na prática, isso torna HTTP/3 especialmente interessante para sistemas que:

- dependem de várias APIs externas
- se preocupam com latência de cauda
- rodam em ambientes distribuídos
- precisam lidar bem com redes imperfeitas
- fazem fan out para múltiplos parceiros

Toda aplicação Java vai ficar mais rápida só porque você definiu `HTTP_3`?

Não.

E é justamente por isso que esse tema merece uma conversa mais honesta.

## A decisão real não é “usar HTTP/3 ou não”

A decisão real é sobre estratégia.

## Estratégia 1: preferir HTTP/3 primeiro

Se você acredita que o serviço remoto já suporta HTTP/3 e quer aproveitar isso quando estiver disponível, esse é o caminho mais direto.

```java
HttpClient client = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_3)
        .build();
```

Isso faz sentido quando existe confiança no ecossistema do parceiro e você quer testar um caminho mais moderno logo de saída.

## Estratégia 2: usar HTTP/3 só em requisições específicas

Nem toda integração merece o mesmo tratamento.

Talvez a API de preços seja uma boa candidata para teste com HTTP/3, enquanto um provedor legado de reservas ainda se comporta melhor com uma negociação mais conservadora.

```java
HttpClient client = HttpClient.newBuilder().build();

HttpRequest requestBusca = HttpRequest.newBuilder()
        .uri(URI.create("https://api.parceiro-viagens.com/hoteis/busca"))
        .version(HttpClient.Version.HTTP_3)
        .GET()
        .build();
```

Essa é a abordagem que eu mais gosto para produção, porque é incremental e mais fácil de observar.

## Estratégia 3: tratar fallback como parte do desenho

Só porque Java 26 suporta HTTP/3 não significa que seu modelo mental deva ser “agora tudo é HTTP/3”.

Em um agregador de viagens, fallback elegante importa mais do que entusiasmo com protocolo.

Seu trabalho não é defender uma posição ideológica sobre transporte.
Seu trabalho é devolver uma boa resposta de forma consistente.

Se um fornecedor não suporta bem HTTP/3, ou se a negociação cair para HTTP/2, isso não é fracasso. Isso é comportamento normal.

A pergunta útil é esta:

essa estratégia de client reduz latência e mantém previsibilidade sob tráfego real?

É isso que interessa.

## O que eu não faria

### “HTTP/3 é automaticamente mais rápido”

Não necessariamente.

Se o seu parceiro está lento por causa de contenção de banco, HTTP/3 não vai te salvar.

### “Agora eu devo forçar HTTP/3 em tudo”

Também não.

Um backend com várias integrações quase sempre vive num ecossistema heterogêneo.
Forçar escolha de protocolo sem medir antes costuma ser pior do que adotar com critério.

### “Isso é só detalhe de client”

Não é.

Em sistemas distribuídos, comportamento de transporte também é comportamento da aplicação.

## O que eu testaria de verdade

Se eu estivesse avaliando adoção de HTTP/3 em backend Java, eu testaria assim:

- escolher uma integração com sensibilidade real a latência
- comparar percentis de resposta, não só média
- observar o comportamento de fallback
- monitorar handshake e reaproveitamento de conexão
- comparar taxa de erro em condição degradada
- evitar tirar conclusão com benchmark local

## Minha conclusão

O suporte a HTTP/3 no `HttpClient` do Java 26 não é só uma feature para preencher release note.

Ele é útil porque dá aos desenvolvedores Java uma forma melhor de lidar com a web moderna usando a biblioteca padrão.

Mas o valor real não está em escrever isto:

```java
.version(HttpClient.Version.HTTP_3)
```

O valor real está em saber onde essa decisão ajuda, onde fallback precisa continuar fazendo parte do desenho, e como a escolha do protocolo afeta o comportamento da aplicação em sistemas distribuídos.

Em um sistema como agregador de viagens, plataforma de reservas, hub logístico ou camada de integração financeira, é aí que essa feature começa a fazer sentido.

Não no anúncio.
Na arquitetura.
