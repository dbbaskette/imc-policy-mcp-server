# Project Instructions: imc-policy-mcp-server

---

## 1. Project Overview & Goal

*   **What is the primary goal of this project?**
    *  THis project uses imc-accident-mcp-server as a basis for creating a net new MCP server.  its copied into here.
    *  We will change the name of the project and the tools it has
    *  it will be named imc-policy-mcp-server, so change the package and any references to the app mcp-server to this new name
    *  This app will have access to query a Vector Store. That vector store will be postgres and should have PGVector enabled
        * In the Cloud profile, that database will be connected via a ClouD FOundry bind
        * In a local profile, the app will spin up a docker container version of postgres locally and enable PGVector
    *  This app will have access to an LLM
        * In the cloud Profile, that will be a model running behind an OPen AI API
        * In the local profile, that will be one of 2 things
            * OpenAI via an Open AI API Key
            * Local Ollama model
    *  This app will have access to an embedding model
        * In the cloud profile, that will be a model running behind an OpenAI API
        * In the local profile, that will be a local OLLAMA based nomic-embed model
    * The app will retain the customer query tool already implmented
    * The input to the MCP server from the client will be a natural Language question,etc and the customeriD  Basically the input from a chatclient. That question input will be sent to the LLM for query expansion. Then that result will be send to the embedding model for embedding and then the results are used to search the VectorDB. We will then use the CustomerID to filter the results down to those specifically for the customerID.  That subset is then sent with the expanded query back to the LLM aand the LLM is asked to formulate an answer SOLELY on the context provided using general model for simplified definitions and explanations of the anwer taken from the context.
    That final answer would then be returned for display in the chatclient.
    * These sets of docs should be your guiding principle.  Always refer to them as your gospel.  When giving code back, always refer back to the area of these docs that you used to forumulate the answer and include that in the plan.
        * RAG Specifics
            * https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html
            * https://docs.spring.io/spring-ai/reference/api/vectordbs/pgvector.html

        * Chat Models
            * https://docs.spring.io/spring-ai/reference/api/chat/openai-chat.html
            * https://docs.spring.io/spring-ai/reference/api/chat/ollama-chat.html
        * Embedding Models
            * https://docs.spring.io/spring-ai/reference/api/embeddings/openai-embeddings.html
            * https://docs.spring.io/spring-ai/reference/api/embeddings/nomic-embed.html
        * MCP Server
            * https://docs.spring.io/spring-ai/reference/api/mcp/mcp-server-boot-starter-docs.html
            * https://docs.spring.io/spring-ai/reference/api/tools.html
            * https://docs.spring.io/spring-ai/reference/api/mcp/mcp-helpers.html
        * Samples - If any of these are not Spring AI 1.0.0 or greater (be careful that nothing new is used after 1.0.0) then use them for pattern only and don't use the code unless you are POSITIVE IT ALL WILL STILL WORK IN 1.0.0
            * https://github.com/dbbaskette/ragui - This was my first attempt at a Chatclient that did the user interface into RAG. In our case the MCP server would be doing most of this work and we would seperate out the input and output of the question and the answer.
            * https://github.com/tzolov/spring-ai-cli-chatbot - Again this is the chatbot doing the work, but we are splitting the work between the MCP Server and the chatbot
            * https://github.com/anjeludo/spring-ai-mcp - Sample showing exposing of Database via MCP server (we have already done this, but worth double checking for issues)
            * https://github.com/habuma/spring-ai-examples/tree/main/spring-ai-rag-example
            * https://github.com/habuma/spring-ai-examples/tree/main/spring-ai-rag-chat



    




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

We will be taking existing code and modifying, expanding, and improving it

## 4. Coding Standards & Conventions

*   **Code Style**: Spring Java

## 5. Important "Do's and Don'ts"

*   **DO**: Write Unit tests
*   **DO**: Log important events and errors.
*   **DO**: create a testing script with colors and graphics that will test the app in both modes.  Use --sse or --stdio on the script and then it can pass that to the cmdline as the correct spring profile.
*   **DO**  Add comments in the code so I can understand whats going on
*.  **DO**  Update teh readme with icons and colors as we complete phases
