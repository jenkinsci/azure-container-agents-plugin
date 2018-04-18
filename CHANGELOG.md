# Azure Container Agents Plugin Changelog

## Version 0.4.2, 2018-04-18
* Use SSH as default launch method
* Update README and help files

## Version 0.4.1, 2018-01-10
* Fix AKS agents after AKS resource API change 

## Version 0.4.0, 2018-01-02
* **Breaking Change**: No longer mount Empty Volume to Working Dir automatically. Make sure that Jenkins have permission
to R/W in Working Dir or mount Empty Volume by yourself   
* Add support for SSH 
* UI change: Hide AcsCredential when choosing AKS, 
* Add more logs in provision ACI for inspecting errors conveniently

## Version 0.3.0, 2017-11-29
* Add support for MSI
* Fix bugs in retention strategy

## Version 0.2.0, 2017-11-3
* Support Azure Kubernetes Service
* Add Third Party Notice
* Various bugs fix

## Version 0.1.2, 2017-10-18
* Remove runtime licenses

## Version 0.1.1, 2017-09-29
* Fixed a guava dependency issue

## Version 0.1.0, 2017-09-27
* Initial release
