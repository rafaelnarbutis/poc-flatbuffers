package main

import (
	"fmt"
	"net/http"
	"server_go/model"

	"github.com/gin-gonic/gin"
	flatbuffers "github.com/google/flatbuffers/go"
)

func getFlatbuffers(c *gin.Context) {
	builder := flatbuffers.NewBuilder(0)

	response := model.UserT{
		Name: "John Doe",
		Age:  30,
	}

	builder.Finish(response.Pack(builder))

	c.Data(http.StatusOK, "application/x-flatbuffers", builder.FinishedBytes())
}

func getJson(c *gin.Context) {

	response := model.UserT{
		Name: "John Doe",
		Age:  30,
	}

	c.JSON(http.StatusOK, response)
}

func main() {
	fmt.Println("Server is running...")

	route := gin.Default()

	route.GET("/flatbuffers", getFlatbuffers)
	route.GET("/json", getJson)

	route.Run(":8081")
}
