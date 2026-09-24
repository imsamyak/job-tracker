const skills = {
    // ── Languages ──────────────────────────────
    "Java": {
        group: {fallback: "Language"},
        prerequisites: [],
        bundledWith: []
    },
    "JavaScript": {
        group: {fallback: "Language"},
        prerequisites: [],
        bundledWith: []
    },
    "TypeScript": {
        group: {fallback: "Language"},
        prerequisites: ["JavaScript"],
        bundledWith: []
    },
    "SQL": {
        group: {fallback: "Language"},
        prerequisites: [],
        bundledWith: []
    },

    // ── Frontend ───────────────────────────────
    "HTML5": {
        group: {fallback: "Language", focused: {"Frontend": "Frontend"}},
        prerequisites: [],
        bundledWith: []
    },
    "CSS3": {
        group: {fallback: "Frontend"},
        prerequisites: ["HTML5"],
        bundledWith: ["Tailwind CSS", "SCSS"]
    },
    "React.js": {
        group: {fallback: "Frontend"},
        prerequisites: ["JavaScript"],
        bundledWith: ["Redux", "Next.js"]
    },
    "Vue.js": {
        group: {fallback: "Frontend"},
        prerequisites: ["JavaScript"],
        bundledWith: ["Nuxt.js"]
    },
    "jQuery": {
        group: {fallback: "Frontend"},
        prerequisites: ["JavaScript"],
        bundledWith: []
    },
    "Tailwind CSS": {
        group: {fallback: "Frontend"},
        prerequisites: ["CSS3"],
        bundledWith: []
    },
    "SCSS": {
        group: {fallback: "Frontend"},
        prerequisites: ["CSS3"],
        bundledWith: []
    },
    "Nuxt.js": {
        group: {fallback: "Frontend"},
        prerequisites: ["Vue.js"],
        bundledWith: ["Server-Side Rendering"]
    },
    "Redux": {
        group: {fallback: "Frontend"},
        prerequisites: ["React.js"],
        bundledWith: []
    },
    "Next.js": {
        group: {fallback: "Frontend"},
        prerequisites: ["React.js"],
        bundledWith: ["Server-Side Rendering"]
    },
    "Server-Side Rendering": {
        group: {fallback: "Frontend"},
        prerequisites: [],
        bundledWith: []
    },

    // ── Backend ────────────────────────────────
    "Node.js": {
        group: {fallback: "Backend"},
        prerequisites: ["JavaScript"],
        bundledWith: ["Express.js"]
    },
    "Express.js": {
        group: {fallback: "Backend"},
        prerequisites: ["Node.js"],
        bundledWith: []
    },
    "REST": {
        group: {fallback: "Backend"},
        prerequisites: [],
        bundledWith: ["OpenAPI", "Postman"]
    },
    "GraphQL": {
        group: {fallback: "Backend"},
        prerequisites: [],
        bundledWith: []
    },
    "OpenAPI": {
        group: {fallback: "Backend"},
        prerequisites: ["REST"],
        bundledWith: []
    },
    "Spring MVC": {
        group: {fallback: "Backend"},
        prerequisites: ["Java"],
        bundledWith: []
    },
    "Spring Boot": {
        group: {fallback: "Backend"},
        prerequisites: ["Spring MVC"],
        bundledWith: []
    },
    "NestJS": {
        group: {fallback: "Backend"},
        prerequisites: ["Node.js", "TypeScript"],
        bundledWith: []
    },
    "Microservices": {
        group: {fallback: "Backend"},
        prerequisites: [],
        bundledWith: ["Kafka", "RabbitMQ", "Docker"]
    },
    "Kafka": {
        group: {fallback: "Backend"},
        prerequisites: [],
        bundledWith: []
    },
    "RabbitMQ": {
        group: {fallback: "Backend"},
        prerequisites: [],
        bundledWith: []
    },
    "Redis": {
        group: {fallback: "Backend", focused: {FullStack: "Database"}},
        prerequisites: [],
        bundledWith: []
    },

    // ── Database ───────────────────────────────
    "MySQL": {
        group: {fallback: "Database"},
        prerequisites: ["SQL"],
        bundledWith: []
    },
    "PostgreSQL": {
        group: {fallback: "Database"},
        prerequisites: ["SQL"],
        bundledWith: []
    },
    "MongoDB": {
        group: {fallback: "Database"},
        prerequisites: [],
        bundledWith: []
    },
    "DynamoDB": {
        group: {fallback: "Database"},
        prerequisites: [],
        bundledWith: []
    },

    // ── Cloud ──────────────────────────────────
    "Docker": {
        group: {fallback: "Cloud", focused: {Backend: "Backend"}},
        prerequisites: [],
        bundledWith: ["Kubernetes"]
    },
    "Kubernetes": {
        group: {fallback: "Cloud"},
        prerequisites: ["Docker"],
        bundledWith: []
    },
    "Nginx": {
        group: {fallback: "Cloud"},
        prerequisites: [],
        bundledWith: []
    },
    "Lambda": {
        group: {fallback: "Cloud"},
        prerequisites: [],
        bundledWith: ["CDK", "CloudWatch"]
    },
    "S3": {
        group: {fallback: "Cloud"},
        prerequisites: [],
        bundledWith: []
    },
    "SQS": {
        group: {fallback: "Cloud"},
        prerequisites: [],
        bundledWith: []
    },
    "SNS": {
        group: {fallback: "Cloud"},
        prerequisites: [],
        bundledWith: []
    },
    "ALB": {
        group: {fallback: "Cloud"},
        prerequisites: [],
        bundledWith: []
    },
    "CloudWatch": {
        group: {fallback: "Cloud"},
        prerequisites: [],
        bundledWith: []
    },
    "CDK": {
        group: {fallback: "Cloud"},
        prerequisites: [],
        bundledWith: []
    },
    "CloudFront": {
        group: {fallback: "Cloud"},
        prerequisites: ["S3"],
        bundledWith: []
    },
    "EC2": {
        group: {fallback: "Cloud"},
        prerequisites: [],
        bundledWith: []
    },
    "RDS": {
        group: {fallback: "Cloud"},
        prerequisites: ["SQL"],
        bundledWith: []
    },
    "VPC": {
        group: {fallback: "Cloud"},
        prerequisites: [],
        bundledWith: []
    },
    "WAF": {
        group: {fallback: "Cloud"},
        prerequisites: [],
        bundledWith: []
    },

    // ── Tooling ────────────────────────────────
    "Git": {
        group: {fallback: "Tooling"},
        prerequisites: [],
        bundledWith: ["GitHub"]
    },
    "GitHub": {
        group: {fallback: "Tooling"},
        prerequisites: ["Git"],
        bundledWith: []
    },
    "Postman": {
        group: {fallback: "Tooling"},
        prerequisites: [],
        bundledWith: []
    },
    "Turborepo": {
        group: {fallback: "Tooling"},
        prerequisites: [],
        bundledWith: []
    },
    "Vitest": {
        group: {fallback: "Tooling"},
        prerequisites: [],
        bundledWith: []
    },
    "Playwright": {
        group: {fallback: "Tooling"},
        prerequisites: [],
        bundledWith: []
    },
    "SonarQube": {
        group: {fallback: "Tooling"},
        prerequisites: [],
        bundledWith: ["CI/CD"]
    },

    // ── Practice / Concepts ────────────────────
    "OOPs": {
        group: {fallback: "Practice"},
        prerequisites: [],
        bundledWith: []
    },
    "Data Structure & Algorithms": {
        group: {fallback: "Practice"},
        prerequisites: [],
        bundledWith: []
    },
    "SOLID Principles": {
        group: {fallback: "Practice"},
        prerequisites: ["OOPs"],
        bundledWith: []
    },
    "Agile Methodologies": {
        group: {fallback: "Practice"},
        prerequisites: [],
        bundledWith: []
    },
    "Domain-Driven Design": {
        group: {fallback: "Practice"},
        prerequisites: ["OOPs"],
        bundledWith: []
    },
    "CI/CD": {
        group: {fallback: "Practice"},
        prerequisites: [],
        bundledWith: []
    },
    "API Design": {
        group: {fallback: "Practice"},
        prerequisites: [],
        bundledWith: ["REST"]
    },
    "Cloud Computing": {
        group: {fallback: "Practice"},
        prerequisites: [],
        bundledWith: []
    },
    "CQRS": {
        group: {fallback: "Practice"},
        prerequisites: ["Domain-Driven Design"],
        bundledWith: []
    },
    "Event-Driven Architecture": {
        group: {fallback: "Practice"},
        prerequisites: ["Microservices"],
        bundledWith: []
    },
    "SAGA Pattern": {
        group: {fallback: "Practice"},
        prerequisites: ["Event-Driven Architecture"],
        bundledWith: []
    },
    "Data Modeling": {
        group: {fallback: "Practice"},
        prerequisites: [],
        bundledWith: []
    },
    "Distributed Systems": {
        group: {fallback: "Practice"},
        prerequisites: [],
        bundledWith: []
    },

    // ── AI-Assisted Development ────────────────
    "GitHub Copilot": {
        group: {fallback: "AI"},
        prerequisites: [],
        bundledWith: ["Claude"]
    },
    "Claude": {
        group: {fallback: "AI"},
        prerequisites: [],
        bundledWith: []
    },
};