package com.devpilot.backend.services.graph;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CodeRelationshipExtractorTest {

    private final CodeRelationshipExtractor extractor = new CodeRelationshipExtractor();

    @Test
    @DisplayName("Extracts Java packages, imports, classes, extends, and implements")
    void extractJava() {
        String javaCode = """
                package com.devpilot.backend.services;
                
                import java.util.List;
                import com.devpilot.backend.repositories.UserRepository;
                
                public class UserService extends BaseService implements IUserService, Serializable {
                    private final UserRepository repo;
                }
                """;

        var result = extractor.extract("src/main/java/UserService.java", javaCode);

        assertThat(result.symbols()).hasSize(4); // 1 package, 2 imports, 1 class
        assertThat(result.symbols().stream().anyMatch(s -> s.name().equals("com.devpilot.backend.services"))).isTrue();
        assertThat(result.symbols().stream().anyMatch(s -> s.name().equals("UserService"))).isTrue();

        List<CodeRelation> relations = result.relations();
        assertThat(relations.stream().anyMatch(r -> r.type() == RelationType.IMPORTS && r.target().equals("com.devpilot.backend.repositories.UserRepository"))).isTrue();
        assertThat(relations.stream().anyMatch(r -> r.type() == RelationType.EXTENDS && r.target().equals("BaseService"))).isTrue();
        assertThat(relations.stream().anyMatch(r -> r.type() == RelationType.IMPLEMENTS && r.target().equals("IUserService"))).isTrue();
    }

    @Test
    @DisplayName("Extracts TypeScript / JavaScript imports, classes, and exported functions")
    void extractTypeScript() {
        String tsCode = """
                import React, { useState } from 'react';
                import { ApiClient } from './api/client';
                
                export interface UserState {
                    id: string;
                }
                
                export default class UserManager extends BaseManager implements IManager {
                }
                
                export async function fetchUser(id: string) {
                    return null;
                }
                """;

        var result = extractor.extract("src/components/UserManager.tsx", tsCode);

        assertThat(result.symbols().stream().anyMatch(s -> s.name().equalsIgnoreCase("react"))).isTrue();
        assertThat(result.symbols().stream().anyMatch(s -> s.name().equals("./api/client"))).isTrue();
        assertThat(result.symbols().stream().anyMatch(s -> s.name().equals("UserManager"))).isTrue();
        assertThat(result.symbols().stream().anyMatch(s -> s.name().equals("UserState"))).isTrue();
        assertThat(result.symbols().stream().anyMatch(s -> s.name().equals("fetchUser"))).isTrue();

        List<CodeRelation> relations = result.relations();
        assertThat(relations.stream().anyMatch(r -> r.type() == RelationType.EXTENDS && r.target().equals("BaseManager"))).isTrue();
        assertThat(relations.stream().anyMatch(r -> r.type() == RelationType.IMPLEMENTS && r.target().equals("IManager"))).isTrue();
    }

    @Test
    @DisplayName("Extracts Python imports, classes with inheritance, and functions")
    void extractPython() {
        String pyCode = """
                import os
                from flask import Flask, request
                
                class AuthHandler(BaseHandler, SecurityMixin):
                    pass
                
                def authenticate(token):
                    pass
                """;

        var result = extractor.extract("app/auth.py", pyCode);

        assertThat(result.symbols().stream().anyMatch(s -> s.name().equals("os"))).isTrue();
        assertThat(result.symbols().stream().anyMatch(s -> s.name().equals("flask"))).isTrue();
        assertThat(result.symbols().stream().anyMatch(s -> s.name().equals("AuthHandler"))).isTrue();
        assertThat(result.symbols().stream().anyMatch(s -> s.name().equals("authenticate"))).isTrue();

        List<CodeRelation> relations = result.relations();
        assertThat(relations.stream().anyMatch(r -> r.type() == RelationType.EXTENDS && r.target().equals("BaseHandler"))).isTrue();
        assertThat(relations.stream().anyMatch(r -> r.type() == RelationType.EXTENDS && r.target().equals("SecurityMixin"))).isTrue();
    }

    @Test
    @DisplayName("Extracts Go packages, single and multi-line imports, structs, and functions")
    void extractGo() {
        String goCode = """
                package main
                
                import (
                    "fmt"
                    "net/http"
                )
                
                type Server struct {
                    port int
                }
                
                func StartServer(port int) {
                }
                """;

        var result = extractor.extract("server.go", goCode);

        assertThat(result.symbols().stream().anyMatch(s -> s.name().equals("main"))).isTrue();
        assertThat(result.symbols().stream().anyMatch(s -> s.name().equals("fmt"))).isTrue();
        assertThat(result.symbols().stream().anyMatch(s -> s.name().equals("net/http"))).isTrue();
        assertThat(result.symbols().stream().anyMatch(s -> s.name().equals("Server"))).isTrue();
        assertThat(result.symbols().stream().anyMatch(s -> s.name().equals("StartServer"))).isTrue();

        List<CodeRelation> relations = result.relations();
        assertThat(relations.stream().anyMatch(r -> r.type() == RelationType.IMPORTS && r.target().equals("net/http"))).isTrue();
        assertThat(relations.stream().anyMatch(r -> r.type() == RelationType.DEFINES && r.target().equals("Server"))).isTrue();
    }

    @Test
    @DisplayName("Returns empty analysis for empty or unsupported file extensions")
    void unsupportedFile() {
        var result = extractor.extract("README.md", "# DevPilot Documentation");
        assertThat(result.symbols()).isEmpty();
        assertThat(result.relations()).isEmpty();
    }
}
