#!/bin/bash

# Run
if [ "$#" -eq 0 ]; then
    java -cp ~/Documents/code/lox/build lox.Lox
else
    java -cp ~/Documents/code/lox/build lox.Lox "$1"
fi