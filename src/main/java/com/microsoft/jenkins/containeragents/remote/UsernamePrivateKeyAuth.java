/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See LICENSE in the project root for license information.
 */

package com.microsoft.jenkins.containeragents.remote;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * SSH authentication credentials with username and private keys.
 */
class UsernamePrivateKeyAuth extends UsernameAuth {
    private final String passPhrase;
    private final List<String> privateKeys;

    UsernamePrivateKeyAuth(String username, String passPhrase, String... privateKeys) {
        this(username, passPhrase, Arrays.asList(privateKeys));
    }

    UsernamePrivateKeyAuth(String username, String passPhrase, Iterable<String> privateKeys) {
        super(username);
        this.passPhrase = passPhrase;
        //noinspection unchecked
        List<String> privateKeyList = new ArrayList<>();
        for (String privateKey : privateKeys) {
            privateKeyList.add(privateKey);
        }
        this.privateKeys = Collections.unmodifiableList(privateKeyList);
    }

    byte[] getPassPhraseBytes() {
        if (passPhrase == null) {
            return null;
        }
        return passPhrase.getBytes(StandardCharsets.UTF_8);
    }

    List<String> getPrivateKeys() {
        return privateKeys;
    }
}
