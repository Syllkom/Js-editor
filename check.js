const fs = require('fs');

const content = fs.readFileSync('app/src/main/java/com/example/ui/editor/CodeEditorScreen.kt', 'utf8');
const lines = content.split('\n');

let depth = 0;
for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    // Remove comments
    let code = line.replace(/\/\/.*$/, '');
    
    // Very naive string literal skip
    code = code.replace(/"(.*?)"/g, '""');
    code = code.replace(/'(.*?)'/g, "''");

    for (const char of code) {
        if (char === '{') {
            depth++;
        } else if (char === '}') {
            depth--;
        }
    }
    if (depth < 0) {
        console.log(`Extra closing bracket at line ${i + 1}`);
        depth = 0; // reset to see multiple
    }
    if (depth === 0) {
        // console.log(`Balanced at line ${i + 1}`);
    }
}

if (depth > 0) {
    console.log(`Unclosed brackets: ${depth}`);
    // Let's re-run to find which top-level function remains unclosed
    let currentFun = "";
    depth = 0;
    for (let i = 0; i < lines.length; i++) {
        let line = lines[i];
        if (line.startsWith("fun ")) currentFun = line;
        
        let code = line.replace(/\/\/.*$/, '');
        code = code.replace(/"(.*?)"/g, '""');
        code = code.replace(/'(.*?)'/g, "''");
        for (const char of code) {
            if (char === '{') {
                if (depth === 0) console.log(`Entered function around line ${i+1}: ${currentFun}`);
                depth++;
            } else if (char === '}') {
                depth--;
                if (depth === 0) console.log(`Exited function at line ${i+1}`);
            }
        }
    }
} else {
    console.log('perfect!');
}
