package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart

class FileRepository(private val fileDao: FileDao) {
    val allFiles: Flow<List<VirtualFile>> = fileDao.getAllFiles()
        .onStart {
            checkAndSeedDatabase()
        }

    suspend fun getFileById(id: Long): VirtualFile? {
        return fileDao.getFileById(id)
    }

    suspend fun insertFile(file: VirtualFile): Long {
        return fileDao.insertFile(file)
    }

    suspend fun updateFile(file: VirtualFile) {
        fileDao.updateFile(file)
    }

    suspend fun deleteFile(file: VirtualFile) {
        fileDao.deleteFile(file)
    }

    private suspend fun checkAndSeedDatabase() {
        if (fileDao.getCount() == 0) {
            // Seed sample files
            val sampleApp = VirtualFile(
                name = "app.js",
                content = """// JavaScript Code Editor - Main Application
import { useState, useEffect } from 'react';
import { fetchUsers, logMessage } from './apiService.js';

const API_URL = 'https://api.example.com';

class App {
  constructor(name) {
    this.name = name;
    this.users = [];
  }

  async start() {
    logMessage("Initializing " + this.name);
    try {
      this.users = await fetchUsers(API_URL);
      this.render();
    } catch (error) {
      logMessage("Error starting app: " + error.message);
    }
  }

  render() {
    const totalUsers = this.users.length;
    console.log(`Loaded ${"$"}{totalUsers} users!`);
    
    // Auto-Close brackets and indentation test
    const formatted = this.users.map(u => {
      return {
        id: u.id,
        fullName: u.firstName + ' ' + u.lastName
      };
    });
  }
}

// Instantiate and start
const app = new App("VS Code Android");
app.start();
""",
                isSystemSample = true
            )

            val sampleApi = VirtualFile(
                name = "apiService.js",
                content = """// API Services & Async Operations Client
// This file can contain minor syntax bugs to verify our "Error Lens" and "Code Actions" plugins!

export async function fetchUsers(baseUrl) {
  const url = baseUrl + '/users';
  console.log("Fetching from: " + url);
  
  // Simulated asynchronous network fetch
  const response = await fetch(url);
  if (!response.ok) {
    throw new Error('Network response was not ok');
  }
  
  const data = await response.json()
  return data; // Notice missing semicolon above (Code actions can fix this!)
}

export function logMessage(message) {
  const timestamp = new Date().toISOString();
  console.log(`[${"$"}{timestamp}] ${"$"}{message}`);
}

// Syntax error example: unclosed brackets or double variable declaration
// Var duplication or parenthesis issues can be tested:
function testSystem() {
  let isDebugging = true;
  if (isDebugging {
    console.log("Debugging is enabled...");
  }
}
""",
                isSystemSample = true
            )

            val sampleMath = VirtualFile(
                name = "mathUtils.js",
                content = """// Mathematics & Utilities Helpers
// Renders code folding, classes, and regular expressions

const PI = 3.14159265359;

export class Geometry {
  static getCircleArea(radius) {
    if (radius <= 0) return 0;
    return PI * radius * radius;
  }

  static getSphereVolume(radius) {
    return (4 / 3) * PI * Math.pow(radius, 3);
  }
}

// Higher order array helpers
export const filterEvens = (arr) => {
  return arr.filter(n => n % 2 === 0);
};

export const sumArray = (arr) => {
  return arr.reduce((acc, curr) => acc + curr, 0);
};

// Clean folding test brackets
function foldMe() {
  const data = {
    nestedValue: true,
    configurations: [
      12,
      24,
      36
    ]
  };
  return data;
}
""",
                isSystemSample = true
            )

            fileDao.insertFile(sampleApp)
            fileDao.insertFile(sampleApi)
            fileDao.insertFile(sampleMath)
        }
    }
}
