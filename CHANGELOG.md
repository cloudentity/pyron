## [Unreleased]
### Fixed
- Use 'rewritePath' and 'rewriteMethod' rule default value

## [1.7.0] - 2021-02-26
### Added
- support for dynamic port registration in Consul

### Changed
- Update version of vertx-tools dependency to 1.6.0
- Deploying 1 instance of ApiServer instead of 2*CPUs - improves performance + allows using dynamic port

## [1.6.0] - 2021-02-04
### Changed
- Update version of vertx-tools dependency to 1.5.0

### Security
- [CVE-2019-17640](https://nvd.nist.gov/vuln/detail/CVE-2019-17640) - Fixed by upgrading vertx to 3.9.5 (transitive via vertx tools)

## [1.5.0] - 2020-01-20
### Added
- pattern matching support in request-transform plugin to enable dynamic scopes

## [1.4.0] - 2020-12-01
### Added
- License
### Changed
- upgraded curl version

## [1.3.2] - 2020-08-14
### Added
- custom properties for authn plugin
### Changed
- upgraded vertx-tools to 1.3.0
### Fixed
- sd-provider/consul and sd-provider/static modules deployment race condition
- drop base-path when not dropping path-prefix

## [1.3.1] - 2020-07-29

### Fixed
- acp-authz plugin can be deployed as api-group plugin

## [1.3.0] - 2020-06-09
### Changed
- upgraded vertx-tools to 1.2.0
- upgraded vertx to 3.9.1

## [1.2.0] - 2020-05-20
### Added
- acp-authz plugin
- 'components' registry
- authnId to Cloudentity AuthnCtx
- add get_tag script

## [1.1.0] - 2020-04-21
### Added
- capability to generate and upload sbom plugin profiles
- flow Properties to AccessLog object
- AccessLog.gateway.failed optional flag (true if call/plugin exception occurred)
- ResponseCtx.targetResponse (original target service response without any transformations)
- plugins per api-group
- MultiOidcClient support for oidc-server configs in map format
- request body streaming, buffering, dropping
- request body max size limit
- methodCtx entity provider in authn plugin
- acp-authz plugin

### Changed
- RoutingCtx moved to flow Properties
- plugin exception is recovered with 500 API response (response plugins are applied to 500 response)
- vertx-tools upgraded to 1.1.0
- plugin logger names contain bus address prefix
- 'authn' and 'cors' plugin modules adjusted for use in api-groups
- rules 'default' attribute is optional

### Fixed
- multipart Content-Type handling
- copying all header values per key

### Removed
- CorrelationCtx

### Security
- [CVE-2019-20330](https://nvd.nist.gov/vuln/detail/CVE-2019-20330) - Fixed by upgrading jackson-databind 2.9.10.1 -> 2.9.10.3
- [CVE-2020-8840](https://nvd.nist.gov/vuln/detail/CVE-2020-8840) - Fixed by upgrading jackson-databind 2.9.10.1 -> 2.9.10.3
- [CVE-2018-20200](https://nvd.nist.gov/vuln/detail/CVE-2018-20200) - Fixed by underlying vertx-tools libthrift 0.12.0 -> 0.13.0
- [CVE-2019-0205](https://nvd.nist.gov/vuln/detail/CVE-2019-0205) - Fixed by underlying vertx-tools libthrift 0.12.0 -> 0.13.0
- [CVE-2019-0210](https://nvd.nist.gov/vuln/detail/CVE-2019-0210) - Fixed by underlying vertx-tools libthrift 0.12.0 -> 0.13.0

## [1.0.0] - 2019-11-27
### Added
- Initial version
