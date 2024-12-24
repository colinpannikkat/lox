#!/bin/bash

# Compile
javac -d build -sourcepath lox lox/*.java

# Run
if [ "$#" -eq 0 ]; then
    java -cp build lox.Lox
else
    java -cp build lox.Lox "$1"
fi