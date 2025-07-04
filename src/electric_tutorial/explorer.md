# Directory Explorer <span id="title-extra"><span>

<div id="nav"></div>

Server-streamed virtual scroll in **~50ish LOC**

<div style="margin-bottom: 2em;"></div>

!target-nochrome[electric-tutorial.explorer/DirectoryExplorer]()

<div style="margin-bottom: 2em;"></div>

What's happening
* Remote file system browser (this is the file system of the container, we exposed a bunch of intermediate state on purpose for you to scroll.)
* 1000s of records, server streamed as you scroll - file system to dom.
* Try holding Page Down to "play the tape forward"
* Try it in <a href="/electric-tutorial.explorer!DirectoryExplorer/">fullscreen mode here</a>
* Try it on your phone!
* Rows have dynamic markup - hierarchy, indentation, directories have a hyperlink but not files
* links work (try it) with inline navigation, **browser history, forward/back, page refresh**, etc! Cool
