# BullyAlgorithm
A leader election program using Bully algorithm written in Java.

How Code is Structured:

Eleection class contains main GUI and main function
Main calls handler thread object to run.
Coordinator once elected sends alive essages to all other processes and they acknowledge.

Simple GUI with 3 buttons and a text area.
Each process starts seperately, when last process starts election call is made by last.
UI has 3 buttons. Election button to manually start election.
Stop button to stop the process.
Restart button to put process again in ring.

How to Run the code:
javac Election.java

java Election
Run this command 5 seperate times to create 5 process.
Code is designed to work with 5 process. It works as desired only when 5 process exist.
Max Process count can be changed in code.
