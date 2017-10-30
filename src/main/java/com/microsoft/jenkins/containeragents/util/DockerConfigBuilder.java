package com.microsoft.jenkins.containeragents.util;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.IdCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import hudson.security.ACL;
import jenkins.authentication.tokens.api.AuthenticationTokens;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.apache.commons.codec.binary.Base64;
import org.jenkinsci.plugins.docker.commons.credentials.DockerRegistryEndpoint;
import org.jenkinsci.plugins.docker.commons.credentials.DockerRegistryToken;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.cloudbees.plugins.credentials.CredentialsMatchers.allOf;
import static com.cloudbees.plugins.credentials.CredentialsMatchers.firstOrNull;
import static com.cloudbees.plugins.credentials.CredentialsMatchers.withId;

/**
 * Builds docker configuration for use of private repository authentication.
 */
public class DockerConfigBuilder {
    private final List<DockerRegistryEndpoint> endpoints = new ArrayList<>();

    public DockerConfigBuilder(final List<DockerRegistryEndpoint> credentials) {
        endpoints.clear();
        for (DockerRegistryEndpoint credential : credentials) {
            endpoints.add(new DockerRegistryEndpoint(DockerRegistryUtils.formatUrlToWithProtocol(credential.getUrl()),
                    credential.getCredentialsId()));
        }
    }


    public String buildDockercfgForKubernetes()
            throws IOException {
        JSONObject auths = buildAuthsObject();
        return Base64.encodeBase64String(auths.toString().getBytes(Charset.defaultCharset()));
    }

    private JSONObject buildAuthsObject() throws IOException {
        JSONObject auths = new JSONObject();
        for (DockerRegistryEndpoint endpoint : this.endpoints) {
            DockerRegistryToken token = AuthenticationTokens.convert(DockerRegistryToken.class,
                    firstOrNull(CredentialsProvider.lookupCredentials(IdCredentials.class,
                            Jenkins.getInstance(),
                            ACL.SYSTEM,
                            Collections.<DomainRequirement>emptyList()),
                            allOf(AuthenticationTokens.matcher(DockerRegistryToken.class),
                            withId(endpoint.getCredentialsId()))));


            if (token == null) {
                // no credentials filled for this entry
                continue;
            }

            JSONObject entry = new JSONObject()
                    .element("email", token.getEmail())
                    .element("auth", token.getToken());
            auths.put(endpoint.getEffectiveUrl().toString(), entry);
        }
        return auths;
    }
}
