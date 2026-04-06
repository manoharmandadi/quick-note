package com.pm;

import io.restassured.RestAssured;
import io.restassured.response.Response;


import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.notNullValue;

public class AuthIntegrationTest {

    @BeforeAll
    static void setup() {
        RestAssured.baseURI = "http://localhost:4004";
    }

    @Test
    public void shouldReturnOKWithValidCreds(){
        String loginPayload = """
                {
                    "email": "testuser@test.com",
                    "password": "password123"
                }
                """;

        Response response = RestAssured.given()
                .contentType("application/json")
                .body(loginPayload)
                .when()
                .post("/auth/login")
                .then()
                .statusCode(200)
                .body("token", notNullValue())
                .extract()
                .response();
        System.out.println(response.jsonPath().getString("token"));
    }

    @Test
    public void shouldReturnUnauthorizedWithInvalidCreds(){
        String loginPayload = """
                {
                    "email": "testuser@test.com",
                    "password": "pass"
                }
                """;

        RestAssured.given()
                .contentType("application/json")
                .body(loginPayload)
                .when()
                .post("/auth/login")
                .then()
                .statusCode(401);

    }

}
