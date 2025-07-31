# Project Instructions: imc-policy-mcp-server

---

## 1. Project Overview & Goal

*   **What is the primary goal of this project?**
    *  THis project uses imc-accident-mcp-server as a basis for creating a net new MCP server.  its copied into here.
    *  We will change the name of the project and the tools it has
    *  it will be named imc-policy-mcp-server, so change the package and any references to the app mcp-server to this new name

      




*   **Who are the end-users?**
    *  Developers of MCP Serves

## 2. Tech Stack

*   **Language(s) & Version(s)**: Java 21
*   **Framework(s)**: Spring Boot 3.5.3
*   **Database(s)**: postgresql
*   **Key Libraries**: Spring AI 1.0.0 (THE GA RELEASE ONLI)
*   **Build/Package Manager**: MVN
*   **Base Package** com.baskettecase


## 3. Architecture & Design

We will be taking existing code and make it generic.  This code is for a weather app, but after we are done it should just be a generic MCP server.  It should have no mention of weather.  Replace the tools with some basic tools.  make one that will just capitalize a sentence of input, and then make another that takes 2 numbers and a math operator and returns the result.

## 4. Coding Standards & Conventions

*   **Code Style**: Spring Java

## 5. Important "Do's and Don'ts"

*   **DO**: Write Unit tests
*   **DO**: Log important events and errors.
*   **DO**: create a testing script with colors and graphics that will test the app in both modes.  Use --sse or --stdio on the script and then it can pass that to the cmdline as the correct spring profile.