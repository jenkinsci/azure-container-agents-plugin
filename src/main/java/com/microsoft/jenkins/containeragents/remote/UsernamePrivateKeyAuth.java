/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See LICENSE in the project root for license information.
 */

package com.microsoft.jenkins.containeragents.remote;

import com.google.common.collect.ImmutableList;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * SSH authentication credentials with username and private keys.
 */
class UsernamePrivateKeyAuth extends UsernameAuth {
    private final String passPhrase;
    private final ImmutableList<String> privateKeys;

    UsernamePrivateKeyAuth(String username, String passPhrase, String... privateKeys) {
        this(username, passPhrase, Arrays.asList(privateKeys));
    }

    UsernamePrivateKeyAuth(String username, String passPhrase, Iterable privateKeys) {
        super(username);
        this.passPhrase = passPhrase;
        //noinspection unchecked
        this.privateKeys = ImmutableList.<String>copyOf(privateKeys);
    }

    byte[] getPassPhraseBytes() {
        if (passPhrase == null) {
            return null;
        }
        return passPhrase.getBytes(StandardCharsets.UTF_8);
    }

    ImmutableList<String> getPrivateKeys() {
        return privateKeys;
    }
}
