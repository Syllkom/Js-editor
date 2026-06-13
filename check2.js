const fs = require('fs');
const content = fs.readFileSync('app/src/main/java/com/example/ui/editor/CodeEditorScreen.kt', 'utf8');
const lines = content.split('\n');

let depth = 0;
for(let i=1320; i<=1920; i++) {
    // very naive filter for string literals
    let line = lines[i].replace(/\/\/.*$/, ''); // remove comments
    
    // carefully remove string constants
    while(true) {
        let matched = false;
        let s1 = line.indexOf('"');
        if (s1 !== -1) {
            let s2 = line.indexOf('"', s1 + 1);
            if (s2 !== -1) {
                line = line.substring(0, s1) + '""' + line.substring(s2 + 1);
                matched = true;
            }
        }
        
        let c1 = line.indexOf("'");
        if (!matched && c1 !== -1) {
            let c2 = line.indexOf("'", c1 + 1);
            if (c2 !== -1) {
                line = line.substring(0, c1) + "''" + line.substring(c2 + 1);
                matched = true;
            }
        }
        
        if (!matched) break;
    }
    
    for (const c of line) {
        if (c === '{') depth++;
        if (c === '}') depth--;
    }
    if (depth < 0) { console.log("NEGATIVE AT " + i); break; }
}
console.log("DEPTH AT END OF EDITOR CANVAS: " + depth);
