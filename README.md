# Azure Container Agents Plugin

> ***Important***: This plug-in is maintained by the Jenkins community and won’t be supported by Microsoft as of February 29, 2024.

Azure Container Agents Plugin can help you to run a container instance as an agent in Jenkins

## How to install
You can install/update this plugin in Jenkins update center (Manage Jenkins -> Manage Plugins, search Azure Container Agents Plugin).

## Pre-requirements
* Service Principal: [Create Service Principal via Azure CLI 2.0](https://docs.microsoft.com/en-us/cli/azure/create-an-azure-service-principal-azure-cli?toc=%2fazure%2fazure-resource-manager%2ftoc.json)
* or Managed Service Identity: [Configure a VM Managed Service Identity (MSI) using the Azure portal](https://docs.microsoft.com/en-us/azure/active-directory/msi-qs-configure-portal-windows-vm)

## Azure Container Instance

[Azure Container Instances](https://docs.microsoft.com/en-us/azure/container-instances/) offers the fastest and simplest way to run a container in Azure, without having to provision any virtual machines and without having to adopt a higher-level service.

## Pre-requirements
* Resource Group in available regions. Get [region availability details](https://azure.microsoft.com/en-us/global-infrastructure/services/?products=container-instances).

## Configure the plugin
1. Jenkins -> Manage Jenkins -> Configure System
2. Press `Add a new cloud` and choose `Azure Container Instance`
3. Specify `Cloud Name` and it should be unique.
4. Choose an existing `Azure Credential` or create a new credential.
5. Choose `Resource Group`.

## Configure the Container Template
1. Specify `Name` and `Labels`
2. Set `Startup Timeout`.
3. Select `Image OS Type`, Windows or Linux.
4. Fill in `Docker Image`. The default image is `jenkins/inbound-agent` and you can also use it as base image.
5. If you use a private registry, you need to specify a credential.
6. Specify a `Command`. Now the `Command` will override the ENTRYPOINT. `Arguments`. `${rootUrl}`, `${secret}`, `${instanceIdentity}` and `${nodeName}` will be replaced with JenkinsUrl, Secret, [Instance identity](https://github.com/jenkinsci/instance-identity-plugin) and ComputerNodeName automatically.
7. Specify the `Working Dir`. Ensure the user has write permission to this directory.
8. Add `Ports`, `Environment Variables` and `Volumes` as needed.
9. Choose a retention strategy. You can get details by clicking the help icon.
10. Specify `CPU Requirement` and `Memory Requirement`, ACI containers costs per second. Find more details in [Container Instances pricing](https://azure.microsoft.com/en-us/pricing/details/container-instances/).

## Configure Azure Container Instance via Groovy Script

You can use the sample below in Manage Jenkins -> Script Console. The sample only contains a few arguments. Find all the arguments in the [builders](src/main/java/com/microsoft/jenkins/containeragents/builders/) package.
```groovy
import com.microsoft.jenkins.containeragents.builders.*

def myCloud = new AciCloudBuilder()
.withCloudName("mycloud")
.withAzureCredentialsId("<Your Credentials Id>")
.withResourceGroup("myResourceGroup")
.addNewTemplate()
    .withName("mytemplate")
    .withLabel("aci")
    .addNewPort("80")
    .addNewEnvVar("key","value")
.endTemplate()
.build()

Jenkins.get().clouds.add(myCloud)
```
```groovy
//inherit template from existing template
import com.microsoft.jenkins.containeragents.builders.*

def baseTemplate = new AciContainerTemplateBuilder()
.withImage("privateImage")
.addNewPort("80")
.addNewEnvVar("key", "value")
.build()

def myCloud = new AciCloudBuilder()
.withCloudName("mycloud")
.withAzureCredentialsId("<Your Credentials Id>")
.withResourceGroup("myResourceGroup")
.addNewTemplateLike(baseTemplate)
    .withName("mytemplate")
    .withLabel("aci")
.endTemplate()
.build()

Jenkins.get().clouds.add(myCloud)
```

<!-- remove this section after August 2021 -->
## Azure Kubernetes Service

If you were previously using this plugin to integrate with AKS you should use the [Kubernetes plugin](https://plugins.jenkins.io/kubernetes/) instead.
