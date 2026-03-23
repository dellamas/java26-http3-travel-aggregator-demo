# java26-http3-travel-aggregator-demo

Demo funcional em Java que simula um agregador de viagens consultando múltiplos fornecedores locais em paralelo com `HttpClient` e virtual threads.

## O que essa demo faz

Ao rodar a aplicação, ela sobe um servidor HTTP local com três fornecedores mock:

- supplier-a
- supplier-b
- supplier-c

Depois disso, a própria aplicação envia requisições concorrentes para esses fornecedores, consolida as ofertas e imprime o resultado ordenado pelo menor preço.

## O que ela demonstra

- uso real de `HttpClient`
- fan out concorrente com virtual threads
- timeouts explícitos
- tratamento básico de erro para fornecedor com falha
- consolidação e ordenação de ofertas
- testes automatizados com servidor local

## Requisitos

- Java 21+
- Maven 3.9+

## Como rodar os testes

```bash
mvn test
```

## Como executar a aplicação

```bash
mvn compile exec:java -Dexec.mainClass=com.dellamas.http3demo.TravelAggregatorApp
```

Saída esperada, aproximada:

```text
Best offers for Gramado:
supplier-b -> Lago Negro Resort (BRL 580.4)
supplier-c -> Centro Premium Stay (BRL 599.99)
supplier-a -> Mountain View Inn (BRL 620.9)
```

## Observação sobre HTTP/3

A demo usa `HttpClient.Version.HTTP_2` por padrão para continuar simples e estável em qualquer ambiente comum.

Se você estiver testando com Java 26 e quiser explorar HTTP/3 de verdade, pode trocar a configuração do client e validar o comportamento contra serviços que suportem esse protocolo.
