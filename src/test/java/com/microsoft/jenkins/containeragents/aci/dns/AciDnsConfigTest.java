package com.microsoft.jenkins.containeragents.aci.dns;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

class AciDnsConfigTest {

    @Test
    void emptyDnServerNamesAreIgnored(){
        AciDnsConfig configUnderTest = new AciDnsConfig();
        configUnderTest.setDnsServers(Arrays.asList(new AciDnsServer("dnsServerName"), new AciDnsServer("")));

        assertThat(configUnderTest.getDnsServers(), hasSize(1));
    }

}