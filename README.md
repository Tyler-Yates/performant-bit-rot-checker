[![Run Tests](https://github.com/Tyler-Yates/performant-bit-rot-checker/actions/workflows/ci.yml/badge.svg)](https://github.com/Tyler-Yates/performant-bit-rot-checker/actions/workflows/ci.yml)
# Overview
This is a rewrite of https://github.com/Tyler-Yates/bit-rot-checker in Java to be more performant.

Files can "rot" over time on a hard drive.
This program can be run periodically to validate that files are free from rot.

## How It Works
This program takes in a list of paths to check.
Every file under each path is processed (though some can be ignored through configuration).

When this program processes a file it has never seen before, it records data about that file in a MongoDB database.
This is the "source of truth" when it comes to bit rot.
If this file deviates from this recorded data at any point in the future, it will be considered corrupted.
For privacy, the file path is hashed using SHA-256.
The CRC-32 and size of the file are also recorded.
This is an intensive process as the entire file must be read.
For very large files, this processing can take several minutes or even hours.

If the program processes a file it has already seen before, it compares the data of the file on disk with what is found in the database.
If there is a difference, the file fails its verification and is logged.
You can find the logs for this program under the `logs` directory in the root of this project.

In case of interruptions, a local SQLite file is created locally at the root of the project to record how recently a file
was verified.
Files that have passed verification recently (timeframe is configurable) will be skipped.

## Prerequisites
You will need a MongoDB database instance in order to use this program.
You can create a [free MongoDB Atlas cluster](https://docs.atlas.mongodb.com/tutorial/deploy-free-tier-cluster/)
(limited in size to 512MB) if you do not want to provision your own.

## Configuration
You will need to create a `config.json` file in the root of this project.
You can use the `config_example.json` file for a starting point:

Fill out your computer-specific information in the `config.json` file.
This file should be ignored by git.

## Running
This program uses Maven for building and running.
