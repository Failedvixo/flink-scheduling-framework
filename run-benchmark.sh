#!/bin/bash

# Script para ejecutar el benchmark de scheduling

# Colores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}=====================================${NC}"
echo -e "${GREEN}Flink Scheduling Framework Benchmark${NC}"
echo -e "${GREEN}=====================================${NC}"
echo ""

# Verificar Java
if ! command -v java &> /dev/null; then
    echo -e "${RED}Error: Java no está instalado${NC}"
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 11 ]; then
    echo -e "${RED}Error: Se requiere Java 11 o superior${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Java version: OK${NC}"

# Verificar Maven
if ! command -v mvn &> /dev/null; then
    echo -e "${RED}Error: Maven no está instalado${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Maven: OK${NC}"
echo ""

# Compilar proyecto
echo -e "${YELLOW}Compilando proyecto...${NC}"
mvn clean package -DskipTests

if [ $? -ne 0 ]; then
    echo -e "${RED}Error en la compilación${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Compilación exitosa${NC}"
echo ""

# Ejecutar benchmark
echo -e "${YELLOW}Ejecutando benchmark...${NC}"
echo ""

java -cp target/flink-scheduling-framework-1.0-SNAPSHOT.jar \
    com.scheduling.framework.SchedulingBenchmarkJob

echo ""
echo -e "${GREEN}=====================================${NC}"
echo -e "${GREEN}Benchmark completado${NC}"
echo -e "${GREEN}=====================================${NC}"