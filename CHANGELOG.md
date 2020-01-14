## [Unreleased]
### Added
- flow Properties to AccessLog object
- AccessLog.gateway.failed optional flag (true if call/plugin exception occurred)
- ResponseCtx.targetResponse (original target service response without any transformations)
- plugins per api-group

### Changed
- RoutingCtx moved to flow Properties
- plugin exception is recovered with 500 API response (response plugins are applied to 500 response)
- vertx-tools upgraded to 1.1.0

### Removed
- CorrelationCtx

## [1.0.0] - 2019-11-27
### Added
- Initial version
