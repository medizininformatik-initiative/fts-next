# Contributing

Thank you for considering contributing to FTSnext! We welcome your feedback, bug reports, and
enhancement suggestions. This document outlines how to contribute to our project.

## How to Obtain the Software

Our project is hosted on GitHub. You can clone the repository using the following command:

```bash
git clone https://github.com/medizininformatik-initiative/fts-next.git
```

## Providing Feedback

### Bug Reports and Enhancement Suggestions

To provide feedback, report bugs, or suggest enhancements, please open an issue on the GitHub
[issue][issues] page. Please also see our [security policy][security] for information on how to
report security issues and vulnerabilities responsibly.

### Discussions

For general discussions or questions, use the GitHub [Discussions][discussions] tab. This is
a great place to engage with the community and get help or provide feedback.

## Contributing to the Software

We welcome contributions via pull requests (PRs). Here's how you can contribute:

1. **Fork the Repository**: Create your own copy of the repository by forking it on GitHub.
2. **Create a Branch**: Create a new branch for your feature or bug fix.
3. **Implement Your Changes**: Make your changes following our coding standards (see below).
4. **Open a Pull Request**: Once your changes are ready, open a PR. Ensure that your PR corresponds
   to an existing issue that documents what needs to be implemented.

*Note that each pull request should have a corresponding issue discussing the changes from a
functional perspective, while discussions in the PR comments should regard implementation
details, code and code quality.*

### Requirements for Acceptable Contributions

* **Coding Standard**: All code should be formatted using `google-java-format`.
* **Code Quality**: CodeQL will be used for static code analysis. Ensure code passes these checks.
* **Testing**: New code must be tested. Code coverage will be collected automatically, and
  the patch diff should be 100%. Maintainers may skip these requirements if there are valid reasons
  to do so.
* **Code Readability**: Use comments to indicate hard-to-understand code snippets, but aim to name
  all entities so that the code is as human-readable as possible.

### Build Tools

We use Maven and Makefiles as build tools. Ensure your development environment is set up to use
these tools.

## License

By contributing to this project, you agree that your contributions will be licensed under
the [Apache License 2.0][license].

[issues]: https://github.com/medizininformatik-initiative/fts-next/issues

[security]: https://github.com/medizininformatik-initiative/fts-next?tab=security-ov-file

[discussions]: https://github.com/medizininformatik-initiative/fts-next/discussions

[license]: https://github.com/medizininformatik-initiative/fts-next?tab=Apache-2.0-1-ov-file
