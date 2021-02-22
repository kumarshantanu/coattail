# coattail Todo and Change Log

## Todo

- [Todo] Tests
- [Todo] Client support
- [Todo] Documentation
- [Todo] Example app
- [Todo] OpenAPI 3.1 support
  - [Todo] Split out JSON-Schema 2020-12 support in another namespace
- [Todo] Process requestBody without `"required": true` attribute
- [Todo] Honour `"required"` attribute for path/query parameters


## [WIP] 0.0.2 / 2021-February-??

- Add support for configurable event logger
- OpenAPI validation error events
  - `coattail.invalid.path.params`
  - `coattail.request.content.type.error`
  - `coattail.request.body.missing`
  - `coattail.request.body.read.error`
  - `coattail.request.body.parse.error`
  - `coattail.request.body.openapi.error`


## 0.0.1 / 2021-February-22

- Fix Ring request body parsing
- Include data-name in type-pred msg in `coattail.openapi/core-schema->operator`
- Turn request parsing exceptions into HTTP 400 response
  - Demunge type-predicate name
  - Format ex-data of the exception message
- Use `[n]` notation to describe data-name for array element
- Consider all numbers valid for OpenAPI core-type `number`


## 0.0.1-alpha1 / 2021-January-07

- Use named Ring middleware functions
- Fix request-body-middleware: Put parsed body under `:data` key in request


## 0.0.1-alpha0 / 2021-January-07

- OpenAPI operations (OpenAPI 3.0)
  - Payload parsing
  - Payload writing
  - Payload defaults
- Ring support
  - Routes generation (needs a routing library)
    - Path-params parsing/validation
    - Query-params parsing/validation
    - Request-body parsing/validation
    - Response validation/writing
