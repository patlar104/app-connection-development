# Contributing to AppConnect

Thank you for your interest in contributing to AppConnect! This document provides guidelines for contributing to the project.

## Code of Conduct

This project follows a code of conduct. By participating, you are expected to uphold this code. Please report unacceptable behavior to the project maintainers.

## Getting Started

### Prerequisites

- Android Studio with AGP 8.13.2+
- JDK 21
- Git
- Physical Android device (Android 10+) for testing CDM features

### Setting Up Development Environment

1. Clone the repository:
   ```bash
   git clone https://github.com/patlar104/app-connection-development.git
   cd app-connection-development
   ```

2. Open the project in Android Studio

3. Sync Gradle dependencies

4. Run the app on a physical device

## Development Workflow

### Branching Strategy

- `main` - Production-ready code
- `develop` - Development branch
- `feature/*` - Feature branches
- `bugfix/*` - Bug fix branches
- `hotfix/*` - Hotfix branches

### Making Changes

1. Create a new branch from `develop`:
   ```bash
   git checkout develop
   git pull origin develop
   git checkout -b feature/your-feature-name
   ```

2. Make your changes following the coding standards

3. Write or update tests for your changes

4. Run tests and linting:
   ```bash
   ./gradlew test
   ./gradlew lint
   ./gradlew detekt
   ./gradlew ktlintCheck
   ```

5. Commit your changes with a descriptive message:
   ```bash
   git commit -m "Add feature: description of your changes"
   ```

6. Push your branch:
   ```bash
   git push origin feature/your-feature-name
   ```

7. Create a Pull Request against `develop`

## Coding Standards

### Kotlin Style Guide

- Follow [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use ktlint for automatic formatting
- Maximum line length: 120 characters

### Architecture

- Follow Clean Architecture principles
- Use MVVM pattern for presentation layer
- Implement Repository pattern for data access
- Use Hilt for dependency injection

### Testing

- Write unit tests for business logic
- Write instrumented tests for database operations
- Aim for meaningful test coverage, not just high percentages
- Use MockK for mocking in tests

### Security

- Never commit sensitive data (API keys, passwords, certificates)
- Use Android Keystore for cryptographic operations
- Follow security best practices for network communication
- Encrypt all sensitive data at rest

## Pull Request Process

1. Update the README.md with details of changes if applicable
2. Update the VERSIONS.md if you modify dependencies
3. Ensure all tests pass
4. Ensure no linting errors
5. Request review from maintainers
6. Address review feedback
7. Once approved, your PR will be merged

## Reporting Bugs

When reporting bugs, please include:

- Android version
- Device model
- Steps to reproduce
- Expected behavior
- Actual behavior
- Relevant logs or screenshots

## Feature Requests

Feature requests are welcome! Please provide:

- Clear description of the feature
- Use case and motivation
- Potential implementation approach
- Any relevant examples or references

## Questions?

Feel free to open an issue for questions or reach out to the maintainers.

Thank you for contributing to AppConnect!
