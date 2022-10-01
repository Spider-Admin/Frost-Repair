# Frost-Repair

I was writing a FrostImporter for [Spider](https://github.com/Spider-Admin/Spider) to automatically import keys from [Frost](https://jtcfrost.sourceforge.net/). Frost stores its data in dbs-files, which are object-oriented embedded databases, called [Perst](https://www.mcobject.com/perst/), made by McObject.

While importing the data, Spider crashed because there were some dangling references in the dbs-files: messages.dbs stores the subject, the author and more metadata of a message, while messagesContents.dbs stores the message itself. I had several messages in messages.dbs, but no message in messagesContents.dbs. According to the subject these missing messages were all spam-messages, so I simply created empty new messages in messagesContents.dbs.

Frost-Repair reads all messages and their content and copies them to a new dbs-file. If the content of a message can't be read, the content is replaced with an empty string. This allowed me to read all relevant dbs-files of my Frost to import keys for Spider.

## Requirements

- [Oracle Java Development Kit 8](https://www.oracle.com/java/technologies/downloads/#java8)
- [Freenet](https://freenetproject.org/)
- [Frost](https://jtcfrost.sourceforge.net/)

## Build

1. Download the source code and extract it.
2. Open a command prompt in the root-directory of the extracted source code.
3. Run the following command: `gradlew distZip`. This will create the zip-archive `build/distributions/Frost-Repair.zip`.
4. Extract the generated zip-archive.

## Run

Run Frost-Repair with `bin/Frost-Repair path-to-Frost` to repair your dbs-files.

## Contact

Author: Spider-Admin

Freemail: spider-admin@tlc66lu4eyhku24wwym6lfczzixnkuofsd4wrlgopp6smrbojf3a.freemail [^1]

Frost: Spider-Admin@Z+d9Knmjd3hQeeZU6BOWPpAAxxs

FMS: Spider-Admin

Sone: [Spider-Admin](http://localhost:8888/Sone/viewSone.html?sone=msXvLpwmDqprlrYZ5ZRZyi7VUcWQ~Wisznv9JkQuSXY) [^2]

I do not regularly read the email associated with GitHub.

## License

Frost-Repair by Spider-Admin@Z+d9Knmjd3hQeeZU6BOWPpAAxxs is licensed under the [Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0).

The package "frost" and its content was taken from [Frost](https://jtcfrost.sourceforge.net/). I slightly changed the code to reduce the dependencies to the other parts of Frost.

[^1]: Freemail requires a running Freenet node
[^2]: Link requires a running Freenet node at http://localhost:8888/
