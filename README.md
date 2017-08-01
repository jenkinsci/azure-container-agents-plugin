# Azure Container Agent Plugin

Azure Container Agent Plugin can help you to run a container as an agent in Jenkins

## Pre-requirements
* Service Principal: [Create Service Principal via Azure CLI 2.0](https://docs.microsoft.com/en-us/cli/azure/create-an-azure-service-principal-azure-cli?toc=%2fazure%2fazure-resource-manager%2ftoc.json)
* Kubernetes Cluster: [Create Kubernetes in Azure](https://docs.microsoft.com/en-us/azure/container-service/kubernetes/)

## Configure the plugin
1. Jenkins -> Manage Jenkins -> Configure System
2. Press `Add a new cloud` and choose `Azure Container Service(Kubernetes)`
3. Specify `Cloud Name` and it should be unique.
4. Choose an existing `Azure Service Principal` or create a new credential.
5. Choose `Resource Group` and `Container Service Name`.
6. Specify `Namespace`
7. Choose an existing `ACS Credential` or create a new one. You can choose one of two different kinds of credentials:
    * SSH Username with private key
    * Microsoft Azure Container Service
8. Press Test Connection to make sure the configurations above are correct.

## Configure the Pod Template
Although Kubernetes supports multi-containers in a Pod, but we only support one container per pod now.

Please ensure JenkinsURL, secret and nodeName passed to container via arguments or environment variables.

1. Specify `Name` and `Labels`
2. Choose a `Docker image`. Please note that the slave will connect with master via JNLP, so make sure JNLP installed in image.
3. If you use a private registry, you need to specify a credential and you have two choose:
    * Use a Private Registry Secret. You need to [create a Secret](https://kubernetes.io/docs/concepts/configuration/secret/) in your Kubernetes cluster in advance and then fill in the Secret name.
    * Use a Private Registry Credential. You just need to fill in the credential and we will create a Secret for you.
4. Specify a `Command` to override the EntryPoint or leave it blank.
5. Specify the `Arguments`. `${rootUrl}`, `${secret}` and `${nodeName}` will be replace with JenkinsUrl, Secret and ComputerNodeName automatically.
6. Specify the `Working Dir`. It's the root dir of you job.
7. Add Environment Variables and Volumes
8. Choose a retention strategy. You can get details in help.
9. Check whether to run container in privileged mode.
10. Specify Request / Limit of the resource Cpu / Memory. Find details in [Managing Compute Resources for Containers](https://kubernetes.io/docs/concepts/configuration/manage-compute-resources-container/)
