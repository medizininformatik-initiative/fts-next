import type {MarkdownRenderer} from 'vitepress';

export function mermaid(md: MarkdownRenderer) {
  const fence = md.renderer.rules.fence!;
  md.renderer.rules.fence = (tokens, idx, options, env, self) => {
    const token = tokens[idx];
    return token.info.trim() === 'mermaid'
      ? `<Mermaid code="${encodeURIComponent(token.content)}" />`
      : fence(tokens, idx, options, env, self);
  };
}
