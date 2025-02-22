import {
  metadata, // connected graph,
  state, // the original non connected metadata objects,
  SAMPLE_DATABASE,
  ORDERS,
} from "__support__/sample_database_fixture";
import {
  copyObjects,
  getMetadata,
  instantiateDatabase,
  instantiateSchema,
  instantiateTable,
  instantiateField,
  instantiateSegment,
  instantiateMetric,
  instantiateQuestion,
} from "metabase/selectors/metadata";
import Metadata from "metabase-lib/metadata/Metadata";
import Database from "metabase-lib/metadata/Database";
import Schema from "metabase-lib/metadata/Schema";
import Table from "metabase-lib/metadata/Table";
import Field from "metabase-lib/metadata/Field";
import Metric from "metabase-lib/metadata/Metric";
import Segment from "metabase-lib/metadata/Segment";
import Question from "metabase-lib/Question";

const NUM_TABLES = Object.keys(state.entities.tables).length;
const NUM_DBS = Object.keys(state.entities.databases).length;
const NUM_FIELDS = Object.keys(state.entities.fields).length;
const NUM_METRICS = Object.keys(state.entities.metrics).length;
const NUM_SEGMENTS = Object.keys(state.entities.segments).length;

// NOTE: Also tests in redux/metadata.spec.js cover the use of metadata selectors
describe("getMetadata", () => {
  it("should properly transfer metadata", () => {
    expect(metadata).toBeInstanceOf(Metadata);

    expect(Object.keys(metadata.databases).length).toEqual(NUM_DBS);
    expect(Object.keys(metadata.tables).length).toEqual(NUM_TABLES);
    expect(Object.keys(metadata.fields).length).toEqual(NUM_FIELDS);
    expect(Object.keys(metadata.metrics).length).toEqual(NUM_METRICS);
    expect(Object.keys(metadata.segments).length).toEqual(NUM_SEGMENTS);
  });

  describe("connected database", () => {
    it("should have the proper number of tables", () => {
      const tableCount = Object.values(metadata.databases).reduce(
        (sum, db) => sum + db.tables.length,
        0,
      );
      expect(tableCount).toEqual(NUM_TABLES);
    });
  });

  describe("connected table", () => {
    const table = metadata.table(ORDERS.id);
    it("should have the proper number of fields", () => {
      // TODO - make this more dynamic
      expect(table.fields.length).toEqual(7);
    });

    it("should have a parent database", () => {
      expect(table.database).toEqual(metadata.database(SAMPLE_DATABASE.id));
    });
  });

  describe("connected field", () => {
    const field = metadata.field(ORDERS.CREATED_AT.id);
    it("should have a parent table", () => {
      expect(field.table).toEqual(metadata.table(ORDERS.id));
    });
  });
});

describe("copyObjects", () => {
  it("should clone each object in the provided mapping of objects", () => {
    const meta = new Metadata();
    const databases = state.entities.databases;
    const copiedDatabases = copyObjects(meta, databases, instantiateDatabase);

    expect(Object.keys(copiedDatabases).length).toEqual(NUM_DBS);

    Object.values(copiedDatabases).map(db => {
      expect(db).toBeInstanceOf(Database);
      expect(db).toHaveProperty("metadata");
      expect(db.metadata).toBe(meta);
    });
  });

  it("should exclude an `objects` entry if it does not have an id property", () => {
    const meta = new Metadata();
    const objects = [{}];
    const copiedObjects = copyObjects(meta, objects, () => {
      throw new Error(
        "This function should not be triggered due to there being no `objects` entries with `id`",
      );
    });

    expect(copiedObjects).toEqual({});
  });
});

describe("instantiateDatabase", () => {
  it("should return an instance of Database", () => {
    const instance = instantiateDatabase({ abc: 123 });
    expect(instance).toBeInstanceOf(Database);
    expect(instance).toHaveProperty("abc", 123);
  });
});

describe("instantiateSchema", () => {
  it("should return an instance of Schema", () => {
    const instance = instantiateSchema({ abc: 123 });
    expect(instance).toBeInstanceOf(Schema);
    expect(instance).toHaveProperty("abc", 123);
  });
});

describe("instantiateTable", () => {
  it("should return an instance of Table", () => {
    const instance = instantiateTable({ abc: 123 });
    expect(instance).toBeInstanceOf(Table);
    expect(instance).toHaveProperty("abc", 123);
  });
});

describe("instantiateField", () => {
  it("should return an instance of Field", () => {
    const instance = instantiateField({ abc: 123 });
    expect(instance).toBeInstanceOf(Field);
    expect(instance).toHaveProperty("abc", 123);
  });
});

describe("instantiateSegment", () => {
  it("should return an instance of Segment", () => {
    const instance = instantiateSegment({ abc: 123 });
    expect(instance).toBeInstanceOf(Segment);
    expect(instance).toHaveProperty("abc", 123);
  });
});

describe("instantiateMetric", () => {
  it("should return an instance of Metric", () => {
    const instance = instantiateMetric({ abc: 123 });
    expect(instance).toBeInstanceOf(Metric);
    expect(instance).toHaveProperty("abc", 123);
  });
});

describe("instantiateQuestion", () => {
  it("should return an instance of Question", () => {
    const instance = instantiateQuestion({ id: 123 }, metadata);
    expect(instance).toBeInstanceOf(Question);
    expect(instance.id()).toBe(123);
    expect(instance.metadata()).toBe(metadata);
  });
});

describe("databasesList", () => {
  it("should filter out saved questions", () => {
    const metadata = getMetadata(state);
    const savedQuestionDb = {
      id: "saved-question-id",
      name: "Saved Questions",
      is_saved_questions: true,
    };
    metadata.databases[savedQuestionDb.id] = savedQuestionDb;

    expect(
      metadata.databasesList().find(d => d.name === "Saved Questions"),
    ).toEqual(savedQuestionDb);
    expect(
      metadata
        .databasesList({ savedQuestions: false })
        .find(d => d.name === "Saved Questions"),
    ).toBeUndefined();
  });
});
