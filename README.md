# MOOCbase
This repo contains the semester long cumulative project for the course CS186 (Introduction to Database Systems). Spread out over 5 projects over the semester, we implemented a fully functional database system, adding support for B+ tree indices, efficient join algorithms, query optimization, multigranularity locking to support concurrent execution of transactions, and database recovery.

Project 1: SQL
	In this project we will be working with the commonly-used Lahman baseball statistics database (our friends at the San Francisco Giants tell us they use it!) The database contains pitching, hitting, and fielding statistics for Major League Baseball from 1871 through 2019. The scope of the project involves writing SQL queries to extract data and populate views with required tuples.

Project 2: B+ Trees
	In this project you'll be implementing B+ tree indices. Here are a few of the most notable points:
		Our implementation of B+ trees does not support duplicate keys. You will throw an exception whenever a duplicate key is inserted.
		Our implementation of B+ trees assumes that inner nodes and leaf nodes can be serialized on a single page. You do not have to support nodes that span multiple pages.
		Our implementation of delete does not rebalance the tree. Thus, the invariant that all non-root leaf nodes in a B+ tree of order d contain between d and 2d entries is broken. Note that actual B+ trees do rebalance after deletion, but we will not be implementing rebalancing trees in this project for the sake of simplicity.

Project 3: Joins and Query Optimization
	In this project you'll be implementing some common join algorithms and a limited version of the Selinger optimizer. This project was split into 2 parts:
		Part 1: In this part, you will implement some join algorithms: block nested loop join, sort merge, and grace hash join.
		Part 2: In this part, you will implement a piece of a relational query optimizer: Plan space search.

Project 4: Concurrency
	This project was split into 2 parts:
		Part 1 (Queuing): In this part you will implement some helpers functions for lock types and the queuing system for locks. The concurrency directory contains a partial implementation of a lock manager (LockManager.java), which you will be completing.
		Part 2 (Multgranularity): In this part, you will implement the middle layer (LockContext) and the declarative layer (in LockUtil). The concurrency directory contains a partial implementation of a lock context (LockContext), which you must complete in this part of the project.

Project 5: Recovery
	In this project you will implement write-ahead logging and support for savepoints, rollbacks, and ACID compliant restart recovery.

Project 6: NoSQL
	In this project you'll be working with a subset of the MovieLens Dataset. Unlike the data set you worked on long ago in Project 1, the table here won't be organized in tables of records but rather as collections of documents! Documents are similar to records in the sense that they are used to group together pieces of data, but unlike the records we covered for SQL databases, documents can have fields that are not primitive data types. In order to do this, we must use a NoSQL database for storing and querying unstructured data using MongoDB.