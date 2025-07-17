Your task is to write the release notes body for release {RELEASE_NAME}.
The following issues should be mentioned:

````````
{ISSUES}
````````

Filter for the most relevant issues.
List issues in the format: `- title #number` (so that GitHub creates links automatically)
If available, check out the issues body for more information.
Only include it if necessary.

If it is a patch release, there is always a bugfix.
Try to identify the bug that has been fixed and mention it in the top note.
Mention that it fixes a bug.

Here are a number of previous releases, try to sound similar to them,
but omit the "Full Changelog" and "Closed Issues" links.

````````
{RELEASES}
````````

Return only the release notes, without further explanation, hints or notices.
