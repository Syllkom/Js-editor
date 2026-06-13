import java.io.File

fun main() {
    val file = File("app/src/main/java/com/example/ui/editor/CodeEditorScreen.kt")
    val text = file.readText()
    var depth = 0
    var lastOpen = -1
    val lines = text.lines()
    for ((i, line) in lines.withIndex()) {
        for (char in line) {
            if (char == '{') {
                depth++
                lastOpen = i + 1
            } else if (char == '}') {
                depth--
            }
        }
        if (depth < 0) {
            println("Extra closing bracket at line ${i+1}")
            return
        }
        if (depth == 0 && i > 170 && i % 100 == 0) {
             // println("Depth 0 at $i")
        }
    }
    if (depth > 0) {
        println("Missing closing brackets! Depth: $depth. Last open at line $lastOpen")
    } else {
        println("All brackets balanced!")
    }
}
