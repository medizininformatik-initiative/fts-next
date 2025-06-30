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

### Commit Messages

We follow established Git best practices for commit messages to maintain a clean, readable project
history. These guidelines are based on widely-adopted standards from the Linux kernel development
and Git community. <sup>[3][^3]</sup>

#### The 50/72 Rule

All commit messages must follow the 50/72 rule: the subject line should be no more than 50
characters, and the body should be wrapped at 72 characters per line. <sup>[2][^2]</sup> This
ensures
commits are readable in various Git tools and terminal displays.

#### Subject Line Requirements

* **Length**: Maximum 50 characters <sup>[1][^1]</sup>
* **Mood**: Use imperative mood (e.g., "Add user authentication" not "Added user authentication"
  or "Adds user authentication") <sup>[2][^2]</sup>
* **Capitalization**: Use Title Case (e.g., "Fix Database Connection Issue") <sup>[1][^1]</sup>
* **Punctuation**: Do not end the subject line with a period <sup>[1][^1]</sup>
* **Single line**: Subject lines must never span multiple lines <sup>[2][^2]</sup>

#### Body Guidelines

* **Optional**: The body is not required for simple, self-explanatory changes <sup>[1][^1]</sup>
* **Purpose**: Use the body to explain the motivation for the change and contrast this with previous
  behavior <sup>[1][^1],[2][^2]</sup>
* **Formatting**: Wrap lines at 72 characters <sup>[1][^1],[2][^3]</sup>
* **Bullet points**: Acceptable if multiple things were changed in one commit <sup>[4][^4]</sup>,
  but
  consider whether the changes should be split into separate commits/PRs instead

#### What NOT to Include

* **Conventional Commits**: We do not use conventional commit prefixes like `feat:`, `fix:`,
  `docs:`, etc. These are unnecessary overhead for our workflow
* **Class/Function Names**: Avoid mentioning specific class names, function names, or code
  identifiers in commit messages unless absolutely necessary for clarity
* **Issue References**: Referencing GitHub issues in commit messages is not required, as we use
  merge commits that automatically include PR numbers, which link to the associated issues

#### Examples

**Good:**

```
Add User Profile Validation

Implement client-side validation for user profile forms to improve
user experience and reduce server load. Validation includes email
format checking and required field validation.
```

**Bad:**

```
feat: added validation to UserProfileForm.validate() method

- fixes #123
- added email validation
- added required field validation for the UserProfileForm class
```

#### Rationale

These guidelines ensure:

* Consistent, professional commit history
* Easy scanning of project changes
* Clear understanding of what and why changes were made
* Compatibility with Git tools and terminal displays
* Efficient code review process

Our merge commit strategy on GitHub automatically captures PR and issue references, making manual
references in individual commits redundant.

#### References

¹ [The Seven Rules of a Great Git Commit Message][^1]  
² [Git Documentation - Contributing to a Project][^2]  
³ [Linux Kernel Submitting Patches Guidelines][^3]  
⁴ [GitHub's Git Commit Message Guidelines][^4]

## License

By contributing to this project, you agree that your contributions will be licensed under
the [Apache License 2.0][license].

[issues]: https://github.com/medizininformatik-initiative/fts-next/issues

[security]: https://github.com/medizininformatik-initiative/fts-next?tab=security-ov-file

[discussions]: https://github.com/medizininformatik-initiative/fts-next/discussions

[license]: https://github.com/medizininformatik-initiative/fts-next?tab=Apache-2.0-1-ov-file

[^1]: https://cbea.ms/git-commit/

[^2]: https://git-scm.com/book/en/v2/Distributed-Git-Contributing-to-a-Project

[^3]: https://www.kernel.org/doc/html/latest/process/submitting-patches.html

[^4]: https://github.com/erlang/otp/wiki/writing-good-commit-messages
