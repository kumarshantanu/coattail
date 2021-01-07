# coattail Todo and Change Log

## Todo

- [Todo] Tests
- [Todo] Client support
- [Todo] Documentation
- [Todo] Example app
- [Todo] OpenAPI 3.1 support


## [WIP] 0.0.1 / 2021-January-??

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
