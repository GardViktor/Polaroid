# Roda os testes unitários dos módulos Kotlin puros (ex.: calculadora) dentro
# de um container Docker (JDK 21 + Gradle), sem Java/Gradle/Android SDK locais.
# O módulo Android (app) é excluído (-PsemAndroid); para ele use ./gradlew test.
# Uso: ./test.ps1              (todos os testes)
#      ./test.ps1 :calculadora:test   (tarefa específica)

$tarefas = @(if ($args.Count -gt 0) { $args } else { "test" })

docker run --rm `
    -v "${PWD}:/project" `
    -v polaroid-gradle-cache:/home/gradle/.gradle `
    -e GRADLE_USER_HOME=/home/gradle/.gradle `
    -w /project `
    gradle:8.14-jdk21 `
    ./gradlew --no-daemon -PsemAndroid @tarefas

exit $LASTEXITCODE
