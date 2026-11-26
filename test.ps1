# Roda os testes unitários dentro de um container Docker (JDK 21 + Gradle),
# sem precisar de Java/Gradle/Android SDK instalados na máquina.
# Uso: ./test.ps1              (todos os testes)
#      ./test.ps1 :calculadora:test   (tarefa específica)

$tarefas = @(if ($args.Count -gt 0) { $args } else { "test" })

docker run --rm `
    -v "${PWD}:/project" `
    -v polaroid-gradle-cache:/home/gradle/.gradle `
    -e GRADLE_USER_HOME=/home/gradle/.gradle `
    -w /project `
    gradle:8.14-jdk21 `
    ./gradlew --no-daemon @tarefas

exit $LASTEXITCODE
