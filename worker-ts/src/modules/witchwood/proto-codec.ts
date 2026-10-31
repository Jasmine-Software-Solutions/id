import { uuidToBytes, type CompiledResourcePath, type Node } from "./resource-path";
import type { PolicyStatement } from "./policy-compiler";

/**
 * Structural form of `witchwood.resource.CompiledResourcePath` as
 * @grpc/proto-loader presents it (camelCase keys, Buffer for bytes).
 */
export interface CompiledResourcePathProto {
  service: string;
  root?: ResourceNodeProto;
}

export interface ResourceNodeProto {
  wildcard?: { greedy: boolean; child?: ResourceNodeProto };
  typed?: {
    type: string;
    definiteId?: Buffer;
    wildcardId?: boolean;
    child?: ResourceNodeProto;
  };
}

export interface PolicyStatementProto {
  id: number;
  effect: string;
  actions: string[];
  resources: CompiledResourcePathProto[];
  condition?: { json: string };
}

export function pathToProto(path: CompiledResourcePath): CompiledResourcePathProto {
  return {
    service: path.service,
    ...(path.root === null ? {} : { root: nodeToProto(path.root) }),
  };
}

function nodeToProto(node: Node): ResourceNodeProto {
  if (node.kind === "wildcard") {
    return {
      wildcard: {
        greedy: node.greedy,
        ...(node.child === null ? {} : { child: nodeToProto(node.child) }),
      },
    };
  }

  return {
    typed: {
      type: node.type,
      ...(node.resourceId.kind === "definite"
        ? { definiteId: uuidToBytes(node.resourceId.id) }
        : { wildcardId: true }),
      ...(node.child === null ? {} : { child: nodeToProto(node.child) }),
    },
  };
}

export function statementToProto(statement: PolicyStatement): PolicyStatementProto {
  return {
    id: statement.id,
    effect: statement.effect,
    actions: statement.actions,
    resources: statement.resources.map(pathToProto),
    // The proto condition is omitted entirely when the policy has none.
    ...(statement.condition.trim().length === 0
      ? {}
      : { condition: { json: statement.condition } }),
  };
}
