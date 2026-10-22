# Apache Camel 4.18 — Simple Language Study Guide

> An independently written, example-driven Markdown companion to the [Apache Camel 4.18 Simple Language reference](https://camel.apache.org/components/4.18.x/languages/simple-language.html). This is a paraphrased study guide, **not a full transcription** of the documentation. Consult the linked official page for exhaustive syntax and version-specific details.

## Contents

1. [What is Simple?](#1-what-is-simple)
2. [Message and exchange expressions](#2-message-and-exchange-expressions)
3. [Headers, properties, and variables](#3-headers-properties-and-variables)
4. [Built-in function families](#4-built-in-function-families)
5. [Conditions and operators](#5-conditions-and-operators)
6. [Accessing Java objects using OGNL](#6-accessing-java-objects-using-ognl)
7. [Practical Spring Boot / Camel examples](#7-practical-spring-boot--camel-examples)
8. [Pitfalls and diagnostics](#8-pitfalls-and-diagnostics)
9. [Practice exercises](#9-practice-exercises)

## 1. What is Simple?
a
Simple is Camel's compact expression language for reading message/exchange data, building text, and evaluating rules inside routes.

```java
.log("Exchange=${exchangeId}, body=${body}")
.setHeader("source", simple("${routeId}"))
.choice()
    .when(simple("${header.priority} == 'HIGH'"))
        .to("direct:urgent")
    .otherwise()
        .to("direct:regular")
.end();
```

- `${...}`: evaluate a Simple expression.
- `$simple{...}`: alternative delimiter, helpful if another template system also uses `${...}`.
- `simple("...")`: use Simple as an expression or predicate in Java DSL.
- `constant("${body}")`: **literal text**, not a dynamic body lookup.
- Expressions are normally evaluated as the exchange passes through the route.

## 2. Message and exchange expressions

| Expression | Use it for |
|---|---|
| `${body}` | Read the current message payload. |
| `${bodyType}` | Inspect the Java type of the payload. |
| `${bodyAs(String)}` | Request body conversion to a string. |
| `${mandatoryBodyAs(String)}` | Require a non-null body while converting. |
| `${bodyOneLine}` | Present a multiline body on one line. |
| `${exchangeId}` | Correlate logs for an individual exchange. |
| `${id}` | Inspect the message's own identifier. |
| `${exchange}` | Access the exchange object. |
| `${camelId}` | Identify the Camel context. |
| `${routeId}` | Identify the route currently executing. |
| `${fromRouteId}` | Identify the route where the exchange began. |
| `${stepId}` | Identify the current route step where available. |
| `${messageHistory}` | Inspect which route processors handled the exchange (if enabled). |
| `${messageHistory(false)}` | Show a less detailed route path for diagnostics. |
| `${logExchange}` | Get formatted exchange diagnostics; beware sensitive information. |
| `${exception.message}` | Inspect an available processing exception. |
| `${exception.stacktrace}` | Inspect available exception details. |

**Mental model:** an **Exchange** carries the **Message** (body and headers) along with processing state such as exchange properties and failure information.

## 3. Headers, properties, and variables

| Expression | Scope / idea |
|---|---|
| `${header.source}` | One named message header. |
| `${headers}` | The entire header map. |
| `${headers.size}` | Count message headers. |
| `${headerAs(priority,Integer)}` | Read a header with type conversion. |
| `${exchangeProperty.workflowId}` | Custom metadata on the exchange. |
| `${variable.customerId}` | Camel exchange variable. |
| `${variables}` | All exchange variables. |
| `${variables.size}` | Number of variables. |
| `${variableAs(customerId,String)}` | Variable converted to a requested type. |

```java
from("direct:submit")
    .setHeader("source", constant("rest-api"))
    .setProperty("workflowId", constant("wf-17"))
    .setVariable("attemptTag", constant("initial"))
    .log("${exchangeId}: ${header.source} / ${exchangeProperty.workflowId} / ${variable.attemptTag}");
```

**Decision guide:** headers travel with the message and may be interpreted by transport components; exchange properties retain metadata for the exchange; variables can hold intermediate values without replacing the message body.

## 4. Built-in function families

The tables below are **selected functions**, grouped for learning. The official page documents additional functions, overloads, and constraints.

### Core and conversion

| Function | Example | Purpose |
|---|---|---|
| `bodyAs(type)` | `${bodyAs(String)}` | Convert payload. |
| `convertTo(type)` | `${convertTo(String)}` | Convert the current message body. |
| `headerAs(name,type)` | `${headerAs(retries,Integer)}` | Convert a header. |
| `bean(name.method)` | `${bean:...}`* | Call a registered bean via supported bean expression syntax. |
| `ref:key` | `${ref:orderService}` | Look up an object in the registry. |
| `length()` | `${length()}` | Payload length, typically in bytes. |
| `size()` | `${size()}` | Number of elements for collections/arrays. |
| `properties:key:default` | `${properties:app.mode:dev}` | Read Camel configuration properties. |
| `kindOfType(exp)` | `${kindOfType(${body})}` | Classify a value as number, string, etc. |

*Bean invocation has several syntax forms; confirm the exact one you need in the official documentation before using it in a route.*

### Lists, arrays, and maps

| Function | Example | Effect |
|---|---|---|
| `list(...)` | `${list('a','b','c')}` | Build a list. |
| `map(...)` | `${map('id',10,'active',true)}` | Build a key/value map. |
| `distinct(...)` | `${distinct('a','a','b')}` | Keep unique values. |
| `reverse(...)` | `${reverse(1,2,3)}` | Reverse a list. |
| `split(separator)` | `${split(',')}` | Break a string into pieces. |
| `collate(size)` | `${collate(100)}` | Partition a collection into batches. |
| `skip(number)` | `${skip(1)}` | Iterate after skipping entries. |
| `createEmpty(kind)` | `${createEmpty(map)}` | Create an empty collection or string. |

### Conditions

| Function | Example | Effect |
|---|---|---|
| `isEmpty` | `${isEmpty}` | Detect null/empty input. |
| `isEmpty(exp)` | `${isEmpty(${header.customerId})}` | Test a selected value. |
| `isNumeric()` | `${isNumeric()}` | Check digit-only content. |
| `isAlpha()` | `${isAlpha()}` | Check letters. |
| `isAlphaNumeric()` | `${isAlphaNumeric()}` | Check letters/digits. |
| `contains(text)` | `${contains('error')}` | Case-insensitive text search. |
| `not(predicate)` | `${not(${isEmpty})}` | Reverse a boolean condition. |
| `iif(predicate,yes,no)` | `${iif(${header.vip} == true,'fast','normal')}` | Choose one of two results. |
| `assert(exp,msg)` | `${assert(${body} != null,'Missing payload')}` | Raise an error when a required condition fails. |

### Dates

| Function | Example | Result |
|---|---|---|
| `date:millis` | `${date:millis}` | Current epoch milliseconds. |
| `date:now:pattern` | `${date:now:yyyy-MM-dd}` | Today's date as text. |
| `date:exchangeCreated:pattern` | `${date:exchangeCreated:HH:mm:ss}` | Exchange-creation time formatted. |
| `date-with-timezone:...` | See official reference | Format using a specified time zone. |

### Math and numbers

| Function | Example | Purpose |
|---|---|---|
| `abs` | `${abs(-9)}` | Magnitude of a number. |
| `sum` | `${sum(5,7,8)}` | Add integer values. |
| `min` / `max` | `${max(2,9,4)}` | Choose an extreme value. |
| `average` | `${average(10,20,30)}` | Average integer values. |
| `ceil` / `floor` | `${ceil(3.4)}` | Round upward/downward. |
| `random` | `${random(1,10)}` | Pseudorandom integer in a range. |
| `range` | `${range(1,5)}` | Sequence of integers. |

### String handling and other families

Camel 4.18 also provides functions for case changes, joining strings, trimming and replacing text, regular-expression operations, and utility functions. The full reference includes these groups and signatures; inspect the [official function tables](https://camel.apache.org/components/4.18.x/languages/simple-language.html#_built_in_simple_functions) rather than guessing arguments.

## 5. Conditions and operators

Simple predicates compare a left-hand expression with another value. **Spaces around operators matter.**

| Operator family | Examples | Meaning |
|---|---|---|
| Equality | `==`, `!=`, `=~` | Equal, unequal, case-insensitive equal. |
| Ordering | `>`, `>=`, `<`, `<=` | Compare values. |
| Text | `contains`, `startsWith`, `endsWith` | Match substrings / prefixes / suffixes. |
| Set membership | `in`, `!in` | Check membership in listed values. |
| Ranges | `range`, `!range` | Check a numeric interval. |
| Pattern | `regex`, `!regex` | Match a regular expression. |
| Boolean combination | `&&`, `||` | Combine conditions. |
| Conditional expression | `? :` | Produce different results for true/false. |
| Fallback | `?:` | Use alternative for false-like values. |

```java
.choice()
    .when(simple("${header.total} >= 1000 && ${header.country} == 'LB'"))
        .to("direct:high-value-local")
    .when(simple("${header.status} in 'NEW,PENDING'"))
        .to("direct:pending")
    .otherwise()
        .to("direct:other")
.end();
```

**Tip:** Use a Predicate for deciding a branch; use an Expression for calculating a field. A Simple expression can often serve either role, depending on the Camel DSL method.

## 6. Accessing Java objects using OGNL

When a body or header contains a Java bean/POJO, Camel supports getter-style property navigation.

```java
// Suppose body is an Order with getCustomer().getName().
.log("Customer: ${body.customer.name}")
.log("Customer: ${body.getCustomer().getName()}")
.log("Safe: ${body?.customer?.name}");
```

Use OGNL for object properties. For raw JSON strings, prefer unmarshalling or an appropriate JSON expression language rather than assuming dotted POJO access will parse JSON.

## 7. Practical Spring Boot / Camel examples

### Example A — trace a timer exchange

```java
@Component
public class TraceRoute extends RouteBuilder {
    @Override
    public void configure() {
        from("timer:demo?period=2000")
            .routeId("timer-demo")
            .setBody(constant("Hello world"))
            .setHeader("source", constant("timer"))
            .log("exchange=${exchangeId}, route=${routeId}, source=${header.source}, body=${body}");
    }
}
```

### Example B — preserve workflow metadata across steps

```java
from("direct:orders")
    .routeId("orders")
    .setProperty("workflowId", header("workflowId"))
    .setHeader("processingStage", constant("validation"))
    .to("bean:orderValidator")
    .setHeader("processingStage", constant("routing"))
    .log("Workflow ${exchangeProperty.workflowId}: ${body}")
    .to("direct:order-routing");
```

### Example C — branch according to metadata

```java
from("direct:order-routing")
    .choice()
        .when(simple("${header.vip} == true"))
            .to("direct:vip")
        .otherwise()
            .to("direct:standard")
    .end();
```

### Example D — build a diagnostic message without replacing the original payload

```java
from("direct:ingest")
    .log("id=${exchangeId} type=${bodyType} headerCount=${headers.size} payload=${body}")
    .to("bean:ingestionService");
```

**Security:** logging the entire `${body}`, `${headers}`, or `${logExchange}` can expose credentials, tokens, and personal data. Prefer explicit safe fields in production.

## 8. Pitfalls and diagnostics

1. **`constant` vs `simple`:** `constant("${exchangeId}")` stores literal characters; `simple("${exchangeId}")` evaluates the value.
2. **Header not set:** `${header.source}` normally evaluates to null if no `source` header exists.
3. **Data types:** a header read from HTTP or a file may be a string; test numeric conversions explicitly.
4. **Message vs exchange:** `${id}` and `${exchangeId}` are not the same identifier.
5. **Null navigation:** object properties can be missing; use null-safe navigation where appropriate.
6. **Debugging:** `${messageHistory}` depends on the relevant history support/configuration.
7. **Complex rules:** prefer beans, JSONata, XPath, or another appropriate language when Simple becomes hard to maintain.
8. **Untrusted workflow definitions:** do not accept arbitrary expressions from users without validation, access controls, and a restricted execution model.

## 9. Practice exercises

### Exercise 1 — exchange and message

Write a timer route that logs both `${exchangeId}` and `${id}`. Compare values across three timer events.

### Exercise 2 — headers and properties

Set a header `source=api`, property `workflowId=wf-42`, and log them. Then send the exchange through `direct:next` and inspect them again.

### Exercise 3 — conditional rules

Build an order route that sends `total >= 1000` to `direct:manual-review` and other orders to `direct:automatic`.

### Exercise 4 — transformations

Start with a POJO body containing `customer.name` and `customer.city`. Use Simple to produce a string summary.

### Exercise 5 — failure logging

Handle a validation exception using `onException` and log a correlation ID and safe error message. Decide whether it should be retried.

---

## Official source and reading path

- **Primary reference:** [Apache Camel 4.18 Simple Language](https://camel.apache.org/components/4.18.x/languages/simple-language.html)
- See its detailed sections for every function signature, string utilities, operators, OGNL, security considerations, and advanced templating.
- This file is an original learning aid, not an authoritative copy of the Apache documentation. For exact function behavior, defer to the linked source and verify against your project's Camel version.
