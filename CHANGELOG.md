## [Unreleased]
### Added
- flow Properties to AccessLog object
- AccessLog.gateway.failed optional flag (true if call/plugin exception occurred)
- ResponseCtx.targetResponse (original target service response without any transformations)
- plugins per api-group
- MultiOidcClient support for oidc-server configs in map format
- request body streaming, buffering, dropping
- request body max size limit

### Changed
- RoutingCtx moved to flow Properties
- plugin exception is recovered with 500 API response (response plugins are applied to 500 response)
- vertx-tools upgraded to 1.1.0
- plugin logger names contain bus address prefix
- 'authn' and 'cors' plugin modules adjusted for use in api-groups

### Fixed
- multipart Content-Type handling
- copying all header values per key

### Removed
- CorrelationCtx

## [1.0.0] - 2019-11-27
### Added
- Initial version
