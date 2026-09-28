/** Takes the optional credential once and removes it from the process environment. */
export function takeStdioAgentCredential(env: { NO8DO_AGENT_CREDENTIAL?: string }): string | undefined {
  const credential = env.NO8DO_AGENT_CREDENTIAL;
  delete env.NO8DO_AGENT_CREDENTIAL;
  return credential;
}
